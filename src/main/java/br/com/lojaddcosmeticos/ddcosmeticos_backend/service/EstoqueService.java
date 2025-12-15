package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private NfceService nfceService;
    @Autowired private TributacaoService tributacaoService;
    @Autowired private FinanceiroService financeiroService;

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO dados) {
        String usuarioLogado = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "SISTEMA";

        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));

        // 1. Gestão de Fornecedor
        Fornecedor fornecedor = null;
        if (dados.getFornecedorCnpj() != null && !dados.getFornecedorCnpj().isBlank()) {
            String documentoLimpo = dados.getFornecedorCnpj().replaceAll("\\D", "");
            fornecedor = fornecedorRepository.findByCpfOuCnpj(documentoLimpo)
                    .orElseGet(() -> {
                        Fornecedor novo = new Fornecedor();
                        novo.setCpfOuCnpj(documentoLimpo);
                        novo.setTipoPessoa(documentoLimpo.length() > 11 ? "JURIDICA" : "FISICA");
                        novo.setRazaoSocial("Fornecedor Auto-Cadastrado (" + documentoLimpo + ")");
                        novo.setAtivo(true);
                        return fornecedorRepository.save(novo);
                    });
        }

        // 2. Classificação Fiscal
        tributacaoService.classificarProduto(produto);

        // 3. Cálculos
        BigDecimal qtdNova = dados.getQuantidade();
        BigDecimal custoUnitarioNota = dados.getPrecoCusto();
        BigDecimal impostosExtras = dados.getValorImpostosAdicionais() != null ? dados.getValorImpostosAdicionais() : BigDecimal.ZERO;

        BigDecimal custoRealUnitario = custoUnitarioNota;
        if (qtdNova.compareTo(BigDecimal.ZERO) > 0 && impostosExtras.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rateioPorUnidade = impostosExtras.divide(qtdNova, 4, RoundingMode.HALF_UP);
            custoRealUnitario = custoUnitarioNota.add(rateioPorUnidade);
        }

        BigDecimal valorTotalBoleto = (qtdNova.multiply(custoUnitarioNota)).add(impostosExtras);

        // 4. Integração Financeira (CORRIGIDA AQUI)
        if (fornecedor != null) {
            // Define padrões caso venha nulo do DTO
            FormaPagamento forma = dados.getFormaPagamento() != null ? dados.getFormaPagamento() : FormaPagamento.BOLETO;
            Integer parcelas = dados.getQuantidadeParcelas() != null ? dados.getQuantidadeParcelas() : 1;

            financeiroService.lancarDespesaDeCompra(
                    fornecedor,
                    valorTotalBoleto,
                    dados.getNumeroNotaFiscal(),
                    forma,
                    parcelas,
                    dados.getDataVencimentoBoleto() // <--- PASSA A DATA DO DTO
            );
        }

        // 5. Atualização PMP e Estoque
        processarEntradaDePedido(produto, qtdNova, custoRealUnitario, fornecedor, dados.getNumeroNotaFiscal());

        // 6. Auditoria (Log extra se necessário)
        if (fornecedor != null && "FISICA".equals(fornecedor.getTipoPessoa())) {
            nfceService.gerarXmlNotaEntradaPF(produto, qtdNova, fornecedor);
        }
    }

    /**
     * Método auxiliar usado tanto pela Entrada Avulsa quanto pelo Recebimento de Pedido.
     */
    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoRealUnitario, Fornecedor fornecedor, String numeroNota) {

        BigDecimal qtdAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;
        BigDecimal pmpAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        BigDecimal valorTotalEstoqueAntigo = qtdAtual.multiply(pmpAtual);
        BigDecimal valorTotalEntradaReal = quantidade.multiply(custoRealUnitario);
        BigDecimal qtdFinal = qtdAtual.add(quantidade);

        BigDecimal novoPmp = custoRealUnitario;
        if (qtdFinal.compareTo(BigDecimal.ZERO) > 0) {
            novoPmp = (valorTotalEstoqueAntigo.add(valorTotalEntradaReal))
                    .divide(qtdFinal, 4, RoundingMode.HALF_UP);
        }

        produto.setQuantidadeEmEstoque(qtdFinal);
        produto.setPrecoMedioPonderado(novoPmp);
        produto.setPrecoCustoInicial(custoRealUnitario);
        produto.setPossuiNfEntrada(true);
        produtoRepository.save(produto);

        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setFornecedor(fornecedor);
        mov.setTipoMovimento("ENTRADA_ESTOQUE");
        mov.setQuantidadeMovimentada(quantidade);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setCustoMovimentado(custoRealUnitario);
        if (numeroNota != null) {
            try { mov.setIdReferencia(Long.parseLong(numeroNota.replaceAll("\\D",""))); } catch (Exception ignored){}
        }
        movimentoEstoqueRepository.save(mov);

        tributacaoService.classificarProduto(produto);
    }

    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dados) {
        String usuarioLogado = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : "SISTEMA";

        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));

        BigDecimal estoqueAntigo = produto.getQuantidadeEmEstoque();
        BigDecimal estoqueNovo = dados.getNovaQuantidadeReal();
        BigDecimal diferenca = estoqueNovo.subtract(estoqueAntigo);

        if (diferenca.compareTo(BigDecimal.ZERO) == 0) return;

        produto.setQuantidadeEmEstoque(estoqueNovo);
        produtoRepository.save(produto);

        boolean isPerda = diferenca.compareTo(BigDecimal.ZERO) < 0;

        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setTipoMovimento(isPerda ? "AJUSTE_SAIDA_PERDA" : "AJUSTE_ENTRADA_SOBRA");
        mov.setQuantidadeMovimentada(diferenca.abs());
        mov.setDataMovimento(LocalDateTime.now());
        mov.setCustoMovimentado(produto.getPrecoMedioPonderado());
        movimentoEstoqueRepository.save(mov);

        if (isPerda && produto.isPossuiNfEntrada()) {
            nfceService.gerarXmlBaixaEstoque(produto, diferenca.abs(), dados.getMotivo());
        }

        Auditoria audit = new Auditoria();
        audit.setTipoEvento("INVENTARIO_" + (isPerda ? "PERDA" : "SOBRA"));
        audit.setUsuarioResponsavel(usuarioLogado);
        audit.setEntidadeAfetada(produto.getDescricao());
        audit.setMensagem("Ajuste Manual: De " + estoqueAntigo + " para " + estoqueNovo + ". Motivo: " + dados.getMotivo());
        auditoriaRepository.save(audit);
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
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

    // NOVIDADE: Serviço Financeiro injetado
    @Autowired private FinanceiroService financeiroService;

    /**
     * REGISTRAR ENTRADA (COMPRA)
     * Fluxo: Estoque -> Custo Real -> Fiscal -> Financeiro
     */
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

            // Busca ou cria fornecedor automaticamente
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

        // 2. Inteligência Tributária (Preenche NCM/Monofásico se faltar)
        tributacaoService.classificarProduto(produto);

        // 3. Cálculo Financeiro da Compra
        BigDecimal qtdNova = dados.getQuantidade();
        BigDecimal custoUnitarioNota = dados.getPrecoCusto(); // Preço que veio no papel
        BigDecimal impostosExtras = dados.getValorImpostosAdicionais() != null ? dados.getValorImpostosAdicionais() : BigDecimal.ZERO;

        // Custo Real Unitário (Landing Cost) = (PreçoNota + RateioImpostos)
        BigDecimal custoRealUnitario = custoUnitarioNota;

        if (qtdNova.compareTo(BigDecimal.ZERO) > 0 && impostosExtras.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rateioPorUnidade = impostosExtras.divide(qtdNova, 4, RoundingMode.HALF_UP);
            custoRealUnitario = custoUnitarioNota.add(rateioPorUnidade);
        }

        // Valor Total do Boleto/Dívida = (Qtd * PreçoNota) + ImpostosExtras
        BigDecimal valorTotalBoleto = (qtdNova.multiply(custoUnitarioNota)).add(impostosExtras);

        // 4. Integração Financeira (Gera Conta a Pagar)
        if (fornecedor != null) {
            financeiroService.lancarDespesaDeCompra(
                    fornecedor,
                    valorTotalBoleto,
                    dados.getNumeroNotaFiscal(),
                    dados.getDataVencimentoBoleto() // Data escolhida ou +30 dias padrão
            );
        }

        // 5. Cálculo do Preço Médio Ponderado (PMP)
        BigDecimal qtdAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;
        BigDecimal pmpAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        BigDecimal valorTotalEstoqueAntigo = qtdAtual.multiply(pmpAtual);
        BigDecimal valorTotalEntradaReal = qtdNova.multiply(custoRealUnitario); // Usa o Custo Real!
        BigDecimal qtdFinal = qtdAtual.add(qtdNova);

        BigDecimal novoPmp = custoRealUnitario; // Se estoque era 0, assume o novo custo
        if (qtdFinal.compareTo(BigDecimal.ZERO) > 0) {
            novoPmp = (valorTotalEstoqueAntigo.add(valorTotalEntradaReal))
                    .divide(qtdFinal, 4, RoundingMode.HALF_UP);
        }

        // 6. Atualização do Produto
        produto.setQuantidadeEmEstoque(qtdFinal);
        produto.setPrecoMedioPonderado(novoPmp);
        produto.setPrecoCustoInicial(custoRealUnitario);
        produto.setPossuiNfEntrada(true);
        produtoRepository.save(produto);

        // 7. Kardex
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setFornecedor(fornecedor);
        mov.setTipoMovimento("ENTRADA_COMPRA");
        mov.setQuantidadeMovimentada(qtdNova);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setCustoMovimentado(custoRealUnitario); // Grava o custo real
        if (dados.getNumeroNotaFiscal() != null) {
            try { mov.setIdReferencia(Long.parseLong(dados.getNumeroNotaFiscal().replaceAll("\\D",""))); } catch (Exception ignored){}
        }
        movimentoEstoqueRepository.save(mov);

        // 8. Fiscal (Entrada de CPF)
        String infoFiscal = "";
        if (fornecedor != null && "FISICA".equals(fornecedor.getTipoPessoa())) {
            nfceService.gerarXmlNotaEntradaPF(produto, qtdNova, fornecedor);
            infoFiscal = " [NOTA ENTRADA CPF GERADA]";
        }

        // 9. Auditoria
        Auditoria audit = new Auditoria();
        audit.setTipoEvento("ESTOQUE_ENTRADA");
        audit.setUsuarioResponsavel(usuarioLogado);
        audit.setEntidadeAfetada("Produto: " + produto.getDescricao());
        audit.setIdEntidadeAfetada(produto.getId());
        audit.setMensagem(String.format("Entrada: %s un. Custo Real: %s. Fin: %s. %s",
                qtdNova, custoRealUnitario, valorTotalBoleto, infoFiscal));
        auditoriaRepository.save(audit);
    }

    /**
     * AJUSTE DE INVENTÁRIO (PERDAS/SOBRAS)
     */
    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dados) {
        String usuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();
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

        // Baixa Fiscal se for perda de produto com nota
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

    /**
     * Método Interno: Processa a entrada física de um item vindo de um Pedido de Compra.
     * NÃO gera financeiro aqui (o PedidoCompraService fará isso de forma agrupada).
     */
    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoRealUnitario, Fornecedor fornecedor, String numeroNota) {

        // 1. Cálculo do PMP (Custo Médio)
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

        // 2. Atualização do Produto
        produto.setQuantidadeEmEstoque(qtdFinal);
        produto.setPrecoMedioPonderado(novoPmp);
        produto.setPrecoCustoInicial(custoRealUnitario);
        produto.setPossuiNfEntrada(true);
        produtoRepository.save(produto);

        // 3. Kardex
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setFornecedor(fornecedor);
        mov.setTipoMovimento("ENTRADA_PEDIDO");
        mov.setQuantidadeMovimentada(quantidade);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setCustoMovimentado(custoRealUnitario);

        if (numeroNota != null) {
            try { mov.setIdReferencia(Long.parseLong(numeroNota.replaceAll("\\D",""))); } catch (Exception ignored){}
        }
        movimentoEstoqueRepository.save(mov);

        // 4. Inteligência Tributária (Garante classificação)
        tributacaoService.classificarProduto(produto);
    }
}
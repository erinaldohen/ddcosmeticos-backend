package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private ConfiguracaoLojaRepository configuracaoLojaRepository;
    @Autowired private SugestaoPrecoRepository sugestaoPrecoRepository;
    @Autowired(required = false) private TributacaoService tributacaoService;
    @Autowired private AuditoriaRepository auditoriaRepository;

    private Usuario getUsuarioLogado() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Usuario) {
                return (Usuario) authentication.getPrincipal();
            }
        } catch (Exception e) {
            log.warn("Não foi possível identificar o usuário logado: {}", e.getMessage());
        }
        return null;
    }

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO entrada) {
        Fornecedor fornecedor = buscarOuCriarFornecedor(entrada);
        Produto produto = produtoRepository.findByCodigoBarras(entrada.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + entrada.getCodigoBarras()));

        if (entrada.getNumeroNotaFiscal() != null && !entrada.getNumeroNotaFiscal().isBlank()) {
            produto.setPossuiNfEntrada(true);
        }

        atualizarEstoqueECusto(produto, entrada.getQuantidade(), entrada.getPrecoCusto());

        try {
            if(tributacaoService != null) tributacaoService.classificarProduto(produto);
        } catch (Exception e) {
            log.warn("Erro na classificação tributária: {}", e.getMessage());
        }

        registrarMovimento(
                produto,
                fornecedor,
                entrada.getQuantidade(),
                entrada.getPrecoCusto(),
                TipoMovimentoEstoque.ENTRADA,
                MotivoMovimentacaoDeEstoque.COMPRA_FORNECEDOR
        );

        registrarContaPagar(entrada, fornecedor);
        verificarNecessidadeReajuste(produto, entrada.getPrecoCusto());

        if ("FISICA".equalsIgnoreCase(fornecedor.getTipoPessoa())) {
            gerarAuditoriaNotaAvulsa(fornecedor);
        }
    }

    private void registrarMovimento(Produto produto, Fornecedor fornecedor, BigDecimal qtd, BigDecimal custo,
                                    TipoMovimentoEstoque tipo, MotivoMovimentacaoDeEstoque motivo) {
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setFornecedor(fornecedor);
        mov.setTipoMovimentoEstoque(tipo);
        mov.setMotivoMovimentacaoDeEstoque(motivo);
        mov.setQuantidadeMovimentada(qtd);
        mov.setCustoMovimentado(custo);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setUsuario(getUsuarioLogado());

        // ADAPTAÇÃO: Usando o nome antigo
        mov.setSaldoAtual(produto.getQuantidadeEmEstoque());

        movimentoEstoqueRepository.save(mov);
    }

    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dto) {
        Produto produto = produtoRepository.findByCodigoBarras(dto.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        MotivoMovimentacaoDeEstoque motivo;
        try {
            motivo = MotivoMovimentacaoDeEstoque.valueOf(dto.getMotivo().toUpperCase());
        } catch (Exception e) {
            throw new ValidationException("Motivo de ajuste inválido: " + dto.getMotivo());
        }

        TipoMovimentoEstoque status = switch (motivo) {
            case AJUSTE_SOBRA, DEVOLUCAO_CLIENTE, CANCELAMENTO_DE_VENDA, COMPRA_FORNECEDOR, ESTOQUE_INICIAL, AJUSTE_MANUAL -> TipoMovimentoEstoque.ENTRADA;
            case AJUSTE_PERDA, AJUSTE_AVARIA, USO_INTERNO, VENDA, DEVOLUCAO_AO_FORNECEDOR -> TipoMovimentoEstoque.SAIDA;
            default -> throw new ValidationException("Motivo não mapeado: " + motivo);
        };

        // ADAPTAÇÃO: Usando getQuantidadeEmEstoque()
        BigDecimal saldoAtual = produto.getQuantidadeEmEstoque() != null
                ? new BigDecimal(produto.getQuantidadeEmEstoque())
                : BigDecimal.ZERO;

        BigDecimal novaQtd;
        if (status == TipoMovimentoEstoque.ENTRADA) {
            novaQtd = saldoAtual.add(dto.getQuantidade());
        } else {
            novaQtd = saldoAtual.subtract(dto.getQuantidade());
        }

        // ADAPTAÇÃO: Usando setQuantidadeEmEstoque() e .intValue()
        produto.setQuantidadeEmEstoque(novaQtd.intValue());

        produtoRepository.save(produto);

        // ADAPTAÇÃO: Usando getPrecoMedioPonderado()
        BigDecimal custoRegistro = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        registrarMovimento(produto, null, dto.getQuantidade(), custoRegistro, status, motivo);
    }

    private void atualizarEstoqueECusto(Produto produto, BigDecimal qtdEntrada, BigDecimal custoEntrada) {
        // ADAPTAÇÃO: getQuantidadeEmEstoque()
        BigDecimal estoqueAtual = produto.getQuantidadeEmEstoque() != null
                ? new BigDecimal(produto.getQuantidadeEmEstoque())
                : BigDecimal.ZERO;

        // ADAPTAÇÃO: getPrecoMedioPonderado()
        BigDecimal pmpAtual = produto.getPrecoMedioPonderado() != null
                ? produto.getPrecoMedioPonderado()
                : BigDecimal.ZERO;

        BigDecimal valorEstoqueAtual = estoqueAtual.multiply(pmpAtual);
        BigDecimal valorEntrada = qtdEntrada.multiply(custoEntrada);
        BigDecimal novaQuantidade = estoqueAtual.add(qtdEntrada);

        if (novaQuantidade.compareTo(BigDecimal.ZERO) == 0) {
            produto.setQuantidadeEmEstoque(0);
            return;
        }

        BigDecimal novoPmp = valorEstoqueAtual.add(valorEntrada)
                .divide(novaQuantidade, 4, RoundingMode.HALF_UP);

        // ADAPTAÇÃO: Nomes antigos
        produto.setQuantidadeEmEstoque(novaQuantidade.intValue());
        produto.setPrecoMedioPonderado(novoPmp);

        // Referência de custo (pode manter precoCusto ou precoCustoInicial conforme sua entidade)
        if (produto.getPrecoCusto() == null || produto.getPrecoCusto().compareTo(BigDecimal.ZERO) == 0) {
            produto.setPrecoCusto(custoEntrada);
        }
        produtoRepository.save(produto);
    }

    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario, Fornecedor fornecedor, String numeroNotaFiscal) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(produto.getCodigoBarras());
        dto.setQuantidade(quantidade);
        dto.setPrecoCusto(custoUnitario);
        dto.setNumeroNotaFiscal(numeroNotaFiscal);
        if (fornecedor != null) dto.setFornecedorCnpj(fornecedor.getCpfOuCnpj());

        dto.setFormaPagamento(FormaDePagamento.BOLETO);
        dto.setQuantidadeParcelas(1);
        dto.setDataVencimentoBoleto(LocalDate.now().plusDays(30));

        registrarEntrada(dto);
    }

    private void registrarContaPagar(EstoqueRequestDTO entrada, Fornecedor fornecedor) {
        BigDecimal valorTotal = entrada.getQuantidade().multiply(entrada.getPrecoCusto());
        int parcelas = (entrada.getQuantidadeParcelas() != null && entrada.getQuantidadeParcelas() > 0) ? entrada.getQuantidadeParcelas() : 1;

        if (isPagamentoAVista(entrada.getFormaPagamento())) {
            parcelas = 1;
        }

        BigDecimal valorParcela = valorTotal.divide(new BigDecimal(parcelas), 2, RoundingMode.HALF_UP);

        for (int i = 1; i <= parcelas; i++) {
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor);
            conta.setDataEmissao(LocalDate.now());

            String desc = "Compra Estoque - NF " + entrada.getNumeroNotaFiscal();
            if (parcelas > 1) desc += " (" + i + "/" + parcelas + ")";
            conta.setDescricao(desc);
            conta.setValorTotal(valorParcela);

            if (isPagamentoAVista(entrada.getFormaPagamento())) {
                conta.setStatus(StatusConta.PAGO);
                conta.setDataPagamento(LocalDate.now());
                conta.setDataVencimento(LocalDate.now());
            } else {
                conta.setStatus(StatusConta.PENDENTE);
                if (entrada.getFormaPagamento() == FormaDePagamento.BOLETO && entrada.getDataVencimentoBoleto() != null) {
                    conta.setDataVencimento(entrada.getDataVencimentoBoleto());
                } else {
                    conta.setDataVencimento(LocalDate.now().plusDays(30L * i));
                }
            }
            contaPagarRepository.save(conta);
        }
    }

    private boolean isPagamentoAVista(FormaDePagamento fp) {
        return fp == FormaDePagamento.DINHEIRO || fp == FormaDePagamento.PIX || fp == FormaDePagamento.DEBITO;
    }

    private void verificarNecessidadeReajuste(Produto produto, BigDecimal novoCusto) {
        ConfiguracaoLoja config = configuracaoLojaRepository.findById(1L).orElse(null);
        if (config == null || produto.getPrecoVenda() == null || produto.getPrecoVenda().compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal lucroBruto = produto.getPrecoVenda().subtract(novoCusto);
        BigDecimal margemAtualPercentual = lucroBruto.divide(produto.getPrecoVenda(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

        if (margemAtualPercentual.compareTo(config.getMargemLucroAlvo()) < 0) {
            boolean existePendente = sugestaoPrecoRepository.existsByProdutoAndStatusPrecificacao(produto, StatusPrecificacao.PENDENTE);

            if (!existePendente) {
                // Cria sugestão de preço...
            }
        }
    }

    private void gerarAuditoriaNotaAvulsa(Fornecedor fornecedor) {
        Auditoria logAuditoria = new Auditoria();
        logAuditoria.setDataHora(LocalDateTime.now());
        logAuditoria.setTipoEvento("EMISSAO_NOTA_ENTRADA");
        logAuditoria.setEntidadeAfetada("Estoque");
        logAuditoria.setMensagem("NOTA DE ENTRADA (CPF) PROCESSADA para " + fornecedor.getNomeFantasia());

        Usuario u = getUsuarioLogado();
        logAuditoria.setUsuarioResponsavel(u != null ? u.getNome() : "SISTEMA_AUTOMATICO");

        auditoriaRepository.save(logAuditoria);
    }

    private Fornecedor buscarOuCriarFornecedor(EstoqueRequestDTO entrada) {
        if (entrada.getFornecedorCnpj() == null) return null;
        String docLimpo = entrada.getFornecedorCnpj().replaceAll("\\D", "");
        return fornecedorRepository.findByCpfOuCnpj(entrada.getFornecedorCnpj())
                .or(() -> fornecedorRepository.findByCpfOuCnpj(docLimpo))
                .orElseGet(() -> {
                    Fornecedor novo = new Fornecedor();
                    novo.setCpfOuCnpj(entrada.getFornecedorCnpj());
                    novo.setRazaoSocial("Fornecedor " + entrada.getFornecedorCnpj());
                    novo.setNomeFantasia("Fornecedor " + entrada.getFornecedorCnpj());
                    novo.setAtivo(true);
                    novo.setTipoPessoa(docLimpo.length() <= 11 ? "FISICA" : "JURIDICA");
                    return fornecedorRepository.save(novo);
                });
    }
}
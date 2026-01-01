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
import java.util.List;

@Slf4j
@Service
public class EstoqueService {

    // ==================================================================================
    // SESSÃO 1: DEPENDÊNCIAS
    // ==================================================================================
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private LoteProdutoRepository loteProdutoRepository; // <--- NOVO
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

    // ==================================================================================
    // SESSÃO 2: ENTRADA DE MERCADORIA (COMPRA/NOTA)
    // ==================================================================================

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO entrada) {
        // 1. Identifica Fornecedor e Produto
        Fornecedor fornecedor = buscarOuCriarFornecedor(entrada);
        Produto produto = produtoRepository.findByCodigoBarras(entrada.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + entrada.getCodigoBarras()));

        // --- ADICIONE ESTA LINHA AQUI (LINHA 61 APROXIMADAMENTE) ---
        int qtdEntrada = entrada.getQuantidade().intValue();
        // -----------------------------------------------------------

        if (entrada.getNumeroNotaFiscal() != null && !entrada.getNumeroNotaFiscal().isBlank()) {
            int saldoFiscalAtual = produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0;
            produto.setEstoqueFiscal(saldoFiscalAtual + qtdEntrada);
        } else {
            int saldoNaoFiscalAtual = produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0;
            produto.setEstoqueNaoFiscal(saldoNaoFiscalAtual + qtdEntrada);
        }

        // 2. Atualiza Custo Médio e Saldo Total do Produto
        atualizarEstoqueECusto(produto, entrada.getQuantidade(), entrada.getPrecoCusto());

        // 3. RASTREABILIDADE: Gerencia Lote e Validade
        if (entrada.getNumeroLote() != null && !entrada.getNumeroLote().isBlank()) {
            registrarLote(produto, entrada);
        }

        // 4. Classificação Fiscal (Automática)
        try {
            if(tributacaoService != null) tributacaoService.classificarProduto(produto);
        } catch (Exception e) {
            log.warn("Erro na classificação tributária: {}", e.getMessage());
        }

        // 5. Registra Histórico (Kardex)
        registrarMovimento(
                produto,
                fornecedor,
                entrada.getQuantidade(),
                entrada.getPrecoCusto(),
                TipoMovimentoEstoque.ENTRADA,
                MotivoMovimentacaoDeEstoque.COMPRA_FORNECEDOR
        );

        // 6. Gera Contas a Pagar
        registrarContaPagar(entrada, fornecedor);

        // 7. Auditoria de Preço
        verificarNecessidadeReajuste(produto, entrada.getPrecoCusto());

        if (fornecedor != null && "FISICA".equalsIgnoreCase(fornecedor.getTipoPessoa())) {
            gerarAuditoriaNotaAvulsa(fornecedor);
        }
    }

    // ==================================================================================
    // SESSÃO 3: AJUSTES E SAÍDAS (VENDA/PERDA)
    // ==================================================================================

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
            case AJUSTE_SOBRA, DEVOLUCAO_CLIENTE, CANCELAMENTO_DE_VENDA, COMPRA_FORNECEDOR, ESTOQUE_INICIAL, AJUSTE_ENTRADA -> TipoMovimentoEstoque.ENTRADA;
            case AJUSTE_PERDA, AJUSTE_AVARIA, USO_INTERNO, VENDA, DEVOLUCAO_AO_FORNECEDOR, AJUSTE_SAIDA -> TipoMovimentoEstoque.SAIDA;
            default -> throw new ValidationException("Motivo não mapeado para entrada/saída: " + motivo);
        };

        // Saldo Atual (Total)
        BigDecimal saldoAtual = produto.getQuantidadeEmEstoque() != null
                ? new BigDecimal(produto.getQuantidadeEmEstoque())
                : BigDecimal.ZERO;

        BigDecimal novaQtd;
        if (status == TipoMovimentoEstoque.ENTRADA) {
            novaQtd = saldoAtual.add(dto.getQuantidade());
            // Nota: Se for entrada de ajuste manual, não temos lote definido aqui (simplificação),
            // ou poderíamos exigir lote no DTO de ajuste também.
        } else {
            novaQtd = saldoAtual.subtract(dto.getQuantidade());

            // Lógica FEFO (First Expired, First Out) para baixa de lote
            baixarEstoqueLotes(produto, dto.getQuantidade());
        }

        if (novaQtd.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Estoque insuficiente para realizar este ajuste de saída.");
        }

        produto.setQuantidadeEmEstoque(novaQtd.intValue());
        produtoRepository.save(produto);

        BigDecimal custoRegistro = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;
        registrarMovimento(produto, null, dto.getQuantidade(), custoRegistro, status, motivo);
    }

    // ==================================================================================
    // SESSÃO 4: LÓGICA DE LOTE (NOVO)
    // ==================================================================================

    private void registrarLote(Produto produto, EstoqueRequestDTO entrada) {
        if (entrada.getDataValidade() == null) {
            // Se tem lote mas não tem validade, definimos uma padrão ou lançamos erro?
            // Vamos assumir tolerância:
            entrada.setDataValidade(LocalDate.now().plusYears(1));
        }

        LoteProduto lote = loteProdutoRepository.findByProdutoAndNumeroLote(produto, entrada.getNumeroLote())
                .orElse(new LoteProduto(produto, entrada.getNumeroLote(), entrada.getDataValidade(), BigDecimal.ZERO));

        // Soma ao saldo do lote
        lote.setQuantidadeAtual(lote.getQuantidadeAtual().add(entrada.getQuantidade()));
        loteProdutoRepository.save(lote);
    }

    /**
     * Baixa o estoque dos lotes seguindo a regra FEFO (Vence primeiro, sai primeiro).
     */
    private void baixarEstoqueLotes(Produto produto, BigDecimal quantidadeParaBaixar) {
        List<LoteProduto> lotes = loteProdutoRepository.findLotesDisponiveis(produto);

        BigDecimal restante = quantidadeParaBaixar;

        for (LoteProduto lote : lotes) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) break;

            if (lote.getQuantidadeAtual().compareTo(restante) >= 0) {
                // Lote tem saldo suficiente para cobrir tudo
                lote.setQuantidadeAtual(lote.getQuantidadeAtual().subtract(restante));
                restante = BigDecimal.ZERO;
            } else {
                // Lote acaba, mas ainda falta baixar mais
                restante = restante.subtract(lote.getQuantidadeAtual());
                lote.setQuantidadeAtual(BigDecimal.ZERO);
            }
            loteProdutoRepository.save(lote);
        }
        // Nota: Se acabar os lotes e sobrar 'restante', significa que o estoque virtual (Produto)
        // estava maior que a soma dos lotes (inconsistência de legado). O sistema permite a baixa no Produto,
        // mas os lotes ficarão zerados.
    }

    // ==================================================================================
    // SESSÃO 5: MÉTODOS AUXILIARES E FINANCEIROS
    // ==================================================================================

    private void atualizarEstoqueECusto(Produto produto, BigDecimal qtdEntrada, BigDecimal custoEntrada) {
        BigDecimal estoqueAtual = produto.getQuantidadeEmEstoque() != null
                ? new BigDecimal(produto.getQuantidadeEmEstoque())
                : BigDecimal.ZERO;

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

        produto.setQuantidadeEmEstoque(novaQuantidade.intValue());
        produto.setPrecoMedioPonderado(novoPmp);

        if (produto.getPrecoCusto() == null || produto.getPrecoCusto().compareTo(BigDecimal.ZERO) == 0) {
            produto.setPrecoCusto(custoEntrada);
        }
        produtoRepository.save(produto);
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
        mov.setSaldoAtual(produto.getQuantidadeEmEstoque());
        movimentoEstoqueRepository.save(mov);
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
                // Lógica de sugestão (omitida para brevidade)
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
}
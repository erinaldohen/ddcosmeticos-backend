package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private LoteProdutoRepository loteProdutoRepository;
    @Autowired private CustoService custoService;
    @Autowired private TributacaoService tributacaoService;

    // ==================================================================================
    // SESSÃO 1: ENTRADAS DE ESTOQUE (Integração com PedidoCompraService e ImportacaoNfeService)
    // ==================================================================================

    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario, Fornecedor fornecedor, String numeroNotaFiscal) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(produto.getCodigoBarras());
        dto.setQuantidade(quantidade);
        dto.setPrecoCusto(custoUnitario);
        dto.setNumeroNotaFiscal(numeroNotaFiscal);
        dto.setFornecedorId(fornecedor.getId());
        dto.setNumeroLote(numeroNotaFiscal); // Usa NF como lote padrão se não informado
        dto.setDataFabricacao(java.time.LocalDate.now());
        dto.setDataValidade(java.time.LocalDate.now().plusYears(2)); // Validade padrão segura

        registrarEntrada(dto);
    }

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO entrada) {
        Produto produto = produtoRepository.findByCodigoBarras(entrada.getCodigoBarras())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com código: " + entrada.getCodigoBarras()));

        Fornecedor fornecedor = resolverFornecedor(entrada);

        BigDecimal quantidadeMovimentada = entrada.getQuantidade();
        int estoqueAtualTotal = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0;
        BigDecimal saldoAnterior = new BigDecimal(estoqueAtualTotal);

        // Atualiza Saldos Fiscais
        atualizarSaldosFiscais(produto, entrada.getNumeroNotaFiscal(), quantidadeMovimentada.intValue());

        // Atualiza Custo Médio e Lotes
        custoService.atualizarCustoMedioPonderado(produto, quantidadeMovimentada, entrada.getPrecoCusto());

        if (entrada.getNumeroLote() != null && !entrada.getNumeroLote().isEmpty()) {
            registrarLote(produto, entrada);
        }

        // Registra no Kardex
        registrarMovimento(produto, fornecedor, TipoMovimentoEstoque.ENTRADA,
                MotivoMovimentacaoDeEstoque.COMPRA_FORNECEDOR, quantidadeMovimentada,
                saldoAnterior, entrada.getNumeroNotaFiscal(), entrada.getPrecoCusto());

        produtoRepository.save(produto);
        tributacaoService.classificarProduto(produto);
    }

    // ==================================================================================
    // SESSÃO 2: SAÍDAS DE ESTOQUE (Integração com VendaService)
    // ==================================================================================

    @Transactional
    public void registrarSaida(Long produtoId, BigDecimal quantidade, String observacao) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        int estoqueAtual = garantirInicializacaoEstoque(produto);

        if (estoqueAtual < quantidade.intValue()) {
            throw new RuntimeException("Estoque insuficiente para a saída. Disponível: " + estoqueAtual);
        }

        int saldoAnterior = estoqueAtual;
        realizarBaixaFiscal(produto, quantidade.intValue());

        // Atualiza Totalizador
        atualizarTotalizador(produto);

        baixarPorLote(produto, quantidade);

        registrarMovimento(produto, null, TipoMovimentoEstoque.SAIDA,
                MotivoMovimentacaoDeEstoque.VENDA, quantidade,
                new BigDecimal(saldoAnterior), observacao, null);

        produtoRepository.save(produto);
    }

    // ==================================================================================
    // SESSÃO 3: AJUSTES E INVENTÁRIO
    // ==================================================================================

    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dados) {
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + dados.getCodigoBarras()));

        int saldoAnterior = garantirInicializacaoEstoque(produto);
        TipoMovimentoEstoque tipo = dados.getMotivo().isEntrada() ? TipoMovimentoEstoque.ENTRADA : TipoMovimentoEstoque.SAIDA;
        int qtdAjuste = dados.getQuantidade().intValue();

        if (tipo == TipoMovimentoEstoque.ENTRADA) {
            produto.setEstoqueNaoFiscal((produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0) + qtdAjuste);
        } else {
            realizarBaixaFiscal(produto, qtdAjuste);
        }

        atualizarTotalizador(produto);

        String obs = dados.getObservacao() != null ? dados.getObservacao() : "Ajuste Manual";
        registrarMovimento(produto, null, tipo, dados.getMotivo(),
                dados.getQuantidade(), new BigDecimal(saldoAnterior),
                dados.getMotivo().name() + ": " + obs, null);

        if (tipo == TipoMovimentoEstoque.SAIDA) {
            baixarPorLote(produto, dados.getQuantidade());
        }
        produtoRepository.save(produto);
    }

    // ==================================================================================
    // SESSÃO 4: INTELIGÊNCIA DE ESTOQUE (Geração de PDF)
    // ==================================================================================

    public List<SugestaoCompraDTO> gerarSugestaoCompras() {
        List<Produto> produtos = produtoRepository.findAll();
        List<SugestaoCompraDTO> sugestoes = new ArrayList<>();

        for (Produto p : produtos) {
            // CORREÇÃO: Voltamos para isAtivo() conforme sua estrutura
            if (Boolean.TRUE.equals(p.isAtivo())) {

                boolean precisaComprar = false;
                int quantidadeSugerida = 0;
                String urgencia = "NORMAL";

                int estoqueAtual = p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : 0;
                int minimo = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0;

                // 1. Ruptura
                if (estoqueAtual <= 0) {
                    precisaComprar = true;
                    urgencia = "CRÍTICO (ZERADO)";
                    quantidadeSugerida = (minimo > 0 ? minimo * 2 : 10);
                }
                // 2. Mínimo
                else if (estoqueAtual <= minimo) {
                    precisaComprar = true;
                    urgencia = "ALERTA (BAIXO)";
                    quantidadeSugerida = (minimo * 2) - estoqueAtual;
                }
                // 3. Previsão Inteligente
                else if (p.getVendaMediaDiaria() != null && p.getDiasParaReposicao() != null) {
                    BigDecimal vendaMedia = p.getVendaMediaDiaria();
                    BigDecimal diasRepo = new BigDecimal(p.getDiasParaReposicao());
                    BigDecimal consumoPrevisto = vendaMedia.multiply(diasRepo);

                    if (new BigDecimal(estoqueAtual).compareTo(consumoPrevisto) < 0) {
                        precisaComprar = true;
                        urgencia = "PREVENTIVO";
                        BigDecimal margem = new BigDecimal("1.3");
                        BigDecimal sugestaoCalc = consumoPrevisto.multiply(margem).subtract(new BigDecimal(estoqueAtual));
                        int sugestaoInt = sugestaoCalc.intValue();
                        if (sugestaoInt > quantidadeSugerida) quantidadeSugerida = sugestaoInt;
                    }
                }

                if (precisaComprar && quantidadeSugerida > 0) {
                    BigDecimal custoUnitario = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
                    BigDecimal custoTotalEstimado = custoUnitario.multiply(new BigDecimal(quantidadeSugerida));

                    sugestoes.add(new SugestaoCompraDTO(
                            p.getCodigoBarras(), p.getDescricao(), p.getMarca(),
                            estoqueAtual, minimo, quantidadeSugerida,
                            urgencia, custoTotalEstimado
                    ));
                }
            }
        }
        return sugestoes;
    }

    // ==================================================================================
    // MÉTODOS AUXILIARES (Refatoração para Limpeza de Código)
    // ==================================================================================

    private Fornecedor resolverFornecedor(EstoqueRequestDTO entrada) {
        if (entrada.getFornecedorId() != null) {
            return fornecedorRepository.findById(entrada.getFornecedorId())
                    .orElseThrow(() -> new RuntimeException("Fornecedor ID não encontrado."));
        } else if (entrada.getFornecedorCnpj() != null) {
            String cnpj = entrada.getFornecedorCnpj().replaceAll("\\D", "");
            return fornecedorRepository.findByCpfOuCnpj(cnpj)
                    .orElseThrow(() -> new RuntimeException("Fornecedor CNPJ não encontrado."));
        }
        throw new RuntimeException("Fornecedor não informado.");
    }

    private int garantirInicializacaoEstoque(Produto p) {
        int atual = p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : 0;
        int f = p.getEstoqueFiscal() != null ? p.getEstoqueFiscal() : 0;
        int nf = p.getEstoqueNaoFiscal() != null ? p.getEstoqueNaoFiscal() : 0;

        if (atual > 0 && (f + nf) == 0) {
            p.setEstoqueNaoFiscal(atual); // Corrige inconsistência legado
            return atual;
        }
        return f + nf;
    }

    private void atualizarSaldosFiscais(Produto p, String notaFiscal, int qtd) {
        if (notaFiscal != null && !notaFiscal.isEmpty()) {
            p.setEstoqueFiscal((p.getEstoqueFiscal() != null ? p.getEstoqueFiscal() : 0) + qtd);
        } else {
            p.setEstoqueNaoFiscal((p.getEstoqueNaoFiscal() != null ? p.getEstoqueNaoFiscal() : 0) + qtd);
        }
        atualizarTotalizador(p);
    }

    private void realizarBaixaFiscal(Produto p, int qtdBaixa) {
        int f = p.getEstoqueFiscal() != null ? p.getEstoqueFiscal() : 0;
        int nf = p.getEstoqueNaoFiscal() != null ? p.getEstoqueNaoFiscal() : 0;

        if (f >= qtdBaixa) {
            p.setEstoqueFiscal(f - qtdBaixa);
        } else {
            int restante = qtdBaixa - f;
            p.setEstoqueFiscal(0);
            p.setEstoqueNaoFiscal(Math.max(0, nf - restante));
        }
    }

    private void atualizarTotalizador(Produto p) {
        int f = p.getEstoqueFiscal() != null ? p.getEstoqueFiscal() : 0;
        int nf = p.getEstoqueNaoFiscal() != null ? p.getEstoqueNaoFiscal() : 0;
        p.setQuantidadeEmEstoque(f + nf);
    }

    private void registrarMovimento(Produto p, Fornecedor f, TipoMovimentoEstoque tipo, MotivoMovimentacaoDeEstoque motivo,
                                    BigDecimal qtd, BigDecimal saldoAnt, String doc, BigDecimal custo) {
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(p);
        mov.setFornecedor(f);
        mov.setTipoMovimentoEstoque(tipo);
        mov.setMotivoMovimentacaoDeEstoque(motivo);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setQuantidadeMovimentada(qtd);
        mov.setSaldoAnterior(saldoAnt.intValue());
        mov.setSaldoAtual(p.getQuantidadeEmEstoque());
        mov.setCustoMovimentado(custo);
        mov.setDocumentoReferencia(doc);
        movimentoEstoqueRepository.save(mov);
    }

    private void registrarLote(Produto p, EstoqueRequestDTO entrada) {
        LoteProduto lote = loteProdutoRepository.findByProdutoAndNumeroLote(p, entrada.getNumeroLote())
                .orElse(new LoteProduto());
        if (lote.getId() == null) {
            lote.setProduto(p);
            lote.setNumeroLote(entrada.getNumeroLote());
            lote.setDataFabricacao(entrada.getDataFabricacao());
            lote.setDataValidade(entrada.getDataValidade());
            lote.setQuantidadeInicial(entrada.getQuantidade());
            lote.setQuantidadeAtual(entrada.getQuantidade());
            lote.setPrecoCusto(entrada.getPrecoCusto());
            lote.setAtivo(true);
        } else {
            lote.setQuantidadeAtual(lote.getQuantidadeAtual().add(entrada.getQuantidade()));
        }
        loteProdutoRepository.save(lote);
    }

    private void baixarPorLote(Produto p, BigDecimal qtd) {
        List<LoteProduto> lotes = loteProdutoRepository.findLotesDisponiveis(p);
        for (LoteProduto lote : lotes) {
            if (qtd.compareTo(BigDecimal.ZERO) <= 0) break;
            if (lote.getQuantidadeAtual().compareTo(qtd) >= 0) {
                lote.setQuantidadeAtual(lote.getQuantidadeAtual().subtract(qtd));
                qtd = BigDecimal.ZERO;
            } else {
                qtd = qtd.subtract(lote.getQuantidadeAtual());
                lote.setQuantidadeAtual(BigDecimal.ZERO);
            }
            loteProdutoRepository.save(lote);
        }
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;

    // Se você não tiver o repositório de Lotes ainda, comente esta linha para testar
    @Autowired(required = false) private LoteProdutoRepository loteProdutoRepository;

    // @Autowired private CustoService custoService; // Descomente quando adicionar o arquivo
    // @Autowired private TributacaoService tributacaoService; // Descomente quando adicionar o arquivo

    // ==================================================================================
    // SESSÃO 1: ENTRADAS (Integração com Compras e Importação XML)
    // ==================================================================================

    /**
     * [ADICIONADO] Método ponte para receber dados do Pedido de Compra e registrar entrada.
     */
    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario, Fornecedor fornecedor, String numeroNotaFiscal) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(produto.getCodigoBarras());
        dto.setQuantidade(quantidade);
        dto.setPrecoCusto(custoUnitario);
        dto.setNumeroNotaFiscal(numeroNotaFiscal);

        if (fornecedor != null) {
            dto.setFornecedorId(fornecedor.getId());
        }

        // Define valores padrão para campos obrigatórios do DTO
        dto.setNumeroLote(numeroNotaFiscal); // Usa NF como lote se não informado
        dto.setDataFabricacao(LocalDate.now());
        dto.setDataValidade(LocalDate.now().plusYears(2)); // Validade padrão de 2 anos

        registrarEntrada(dto);
    }

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO entrada) {
        Produto produto = produtoRepository.findByCodigoBarras(entrada.getCodigoBarras())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + entrada.getCodigoBarras()));

        Fornecedor fornecedor = resolverFornecedor(entrada);

        BigDecimal quantidadeMovimentada = entrada.getQuantidade();
        int estoqueAtualTotal = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0;
        BigDecimal saldoAnterior = new BigDecimal(estoqueAtualTotal);

        atualizarSaldosFiscais(produto, entrada.getNumeroNotaFiscal(), quantidadeMovimentada.intValue());

        // custoService.atualizarCustoMedioPonderado(produto, quantidadeMovimentada, entrada.getPrecoCusto());

        if (entrada.getNumeroLote() != null && !entrada.getNumeroLote().isEmpty() && loteProdutoRepository != null) {
            registrarLote(produto, entrada);
        }

        registrarMovimento(produto, fornecedor, TipoMovimentoEstoque.ENTRADA,
                MotivoMovimentacaoDeEstoque.COMPRA_FORNECEDOR, quantidadeMovimentada,
                saldoAnterior, entrada.getNumeroNotaFiscal(), entrada.getPrecoCusto(), null);

        produtoRepository.save(produto);
        // tributacaoService.classificarProduto(produto);
    }

    // ==================================================================================
    // SESSÃO 2: INTEGRAÇÃO COM VENDAS E AJUSTES
    // ==================================================================================

    /**
     * Método principal chamado pelo VendaService para baixar estoque.
     */
    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dados) {
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado no estoque: " + dados.getCodigoBarras()));

        int saldoAnterior = garantirInicializacaoEstoque(produto);

        // Verifica se é entrada ou saída baseado no Motivo
        TipoMovimentoEstoque tipo = dados.getMotivo().isEntrada() ? TipoMovimentoEstoque.ENTRADA : TipoMovimentoEstoque.SAIDA;
        int qtdAjuste = dados.getQuantidade().intValue();

        if (tipo == TipoMovimentoEstoque.ENTRADA) {
            // Entradas manuais geralmente vão para estoque não fiscal (ajuste de sobra)
            produto.setEstoqueNaoFiscal((produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0) + qtdAjuste);
        } else {
            // Saídas (Vendas/Perdas)
            // Verifica saldo antes de baixar
            int estoqueTotal = (produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0)
                    + (produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0);

            if (estoqueTotal < qtdAjuste) {
                throw new RuntimeException("Estoque insuficiente para o produto: " + produto.getDescricao()
                        + ". Atual: " + estoqueTotal + ", Necessário: " + qtdAjuste);
            }
            realizarBaixaFiscal(produto, qtdAjuste);
        }

        atualizarTotalizador(produto);

        String obs = dados.getObservacao() != null ? dados.getObservacao() : "Movimentação Automática";

        registrarMovimento(produto, null, tipo, dados.getMotivo(),
                dados.getQuantidade(), new BigDecimal(saldoAnterior),
                null, null, obs);

        if (tipo == TipoMovimentoEstoque.SAIDA && loteProdutoRepository != null) {
            baixarPorLote(produto, dados.getQuantidade());
        }
        produtoRepository.save(produto);
    }

    // ==================================================================================
    // SESSÃO 3: INTELIGÊNCIA DE ESTOQUE (Relatório de Compras)
    // ==================================================================================

    public List<SugestaoCompraDTO> gerarSugestaoCompras() {
        List<Produto> produtos = produtoRepository.findAll();
        List<SugestaoCompraDTO> sugestoes = new ArrayList<>();

        for (Produto p : produtos) {
            // Verifica se produto está ativo
            if (p.isAtivo()) {
                boolean precisaComprar = false;
                int quantidadeSugerida = 0;
                String urgencia = "NORMAL";

                int estoqueAtual = p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : 0;
                int minimo = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0;

                if (estoqueAtual <= 0) {
                    precisaComprar = true;
                    urgencia = "CRÍTICO (ZERADO)";
                    quantidadeSugerida = (minimo > 0 ? minimo * 2 : 10);
                } else if (estoqueAtual <= minimo) {
                    precisaComprar = true;
                    urgencia = "ALERTA (BAIXO)";
                    quantidadeSugerida = (minimo * 2) - estoqueAtual;
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
    // MÉTODOS AUXILIARES
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
        return null;
    }

    private int garantirInicializacaoEstoque(Produto p) {
        int atual = p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : 0;
        int f = p.getEstoqueFiscal() != null ? p.getEstoqueFiscal() : 0;
        int nf = p.getEstoqueNaoFiscal() != null ? p.getEstoqueNaoFiscal() : 0;

        if (atual > 0 && (f + nf) == 0) {
            p.setEstoqueNaoFiscal(atual);
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
                                    BigDecimal qtd, BigDecimal saldoAnt, String doc, BigDecimal custo, String observacao) {
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
        mov.setObservacao(observacao);
        movimentoEstoqueRepository.save(mov);
    }

    private void registrarLote(Produto p, EstoqueRequestDTO entrada) {
        if (loteProdutoRepository == null) return;

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
        if (loteProdutoRepository == null) return;

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
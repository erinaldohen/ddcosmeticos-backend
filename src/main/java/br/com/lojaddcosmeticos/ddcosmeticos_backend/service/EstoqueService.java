package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private LoteProdutoRepository loteProdutoRepository;
    @Autowired private CustoService custoService;
    @Autowired private TributacaoService tributacaoService;

    // --- Ponte para Pedido de Compra ---
    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario, Fornecedor fornecedor, String numeroNotaFiscal) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(produto.getCodigoBarras());
        dto.setQuantidade(quantidade);
        dto.setPrecoCusto(custoUnitario);
        dto.setNumeroNotaFiscal(numeroNotaFiscal);
        dto.setFornecedorId(fornecedor.getId());

        dto.setNumeroLote(numeroNotaFiscal);
        dto.setDataFabricacao(java.time.LocalDate.now());
        dto.setDataValidade(java.time.LocalDate.now().plusYears(2));

        registrarEntrada(dto);
    }

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO entrada) {
        Produto produto = produtoRepository.findByCodigoBarras(entrada.getCodigoBarras())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com código: " + entrada.getCodigoBarras()));

        // Busca de Fornecedor
        Fornecedor fornecedor;
        if (entrada.getFornecedorId() != null) {
            fornecedor = fornecedorRepository.findById(entrada.getFornecedorId())
                    .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado pelo ID."));
        } else if (entrada.getFornecedorCnpj() != null && !entrada.getFornecedorCnpj().isBlank()) {
            String cnpjLimpo = entrada.getFornecedorCnpj().replaceAll("\\D", "");
            fornecedor = fornecedorRepository.findByCpfOuCnpj(cnpjLimpo)
                    .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado pelo Documento: " + entrada.getFornecedorCnpj()));
        } else {
            throw new RuntimeException("É necessário informar o ID ou CNPJ do fornecedor.");
        }

        BigDecimal quantidadeMovimentada = entrada.getQuantidade();

        // Carrega saldo atual
        int estoqueAtualTotal = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0;
        BigDecimal saldoAnterior = new BigDecimal(estoqueAtualTotal);

        // Atualização de Saldo Fiscal/Não Fiscal
        if (entrada.getNumeroNotaFiscal() != null && !entrada.getNumeroNotaFiscal().isEmpty()) {
            int qtdAtual = produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0;
            produto.setEstoqueFiscal(qtdAtual + quantidadeMovimentada.intValue());
        } else {
            int qtdAtual = produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0;
            produto.setEstoqueNaoFiscal(qtdAtual + quantidadeMovimentada.intValue());
        }

        // Atualiza Totalizador
        int fiscal = produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0;
        int naoFiscal = produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0;
        produto.setQuantidadeEmEstoque(fiscal + naoFiscal);

        // Atualiza PMP e Lote
        custoService.atualizarCustoMedioPonderado(produto, quantidadeMovimentada, entrada.getPrecoCusto());

        if (entrada.getNumeroLote() != null && !entrada.getNumeroLote().isEmpty()) {
            registrarLote(produto, entrada);
        }

        // Kardex
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produto);
        movimento.setFornecedor(fornecedor);
        movimento.setTipoMovimentoEstoque(TipoMovimentoEstoque.ENTRADA);
        movimento.setMotivoMovimentacaoDeEstoque(MotivoMovimentacaoDeEstoque.COMPRA_FORNECEDOR);
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setQuantidadeMovimentada(quantidadeMovimentada);
        movimento.setSaldoAnterior(saldoAnterior.intValue());
        movimento.setSaldoAtual(produto.getQuantidadeEmEstoque());
        movimento.setCustoMovimentado(entrada.getPrecoCusto());
        movimento.setDocumentoReferencia(entrada.getNumeroNotaFiscal());

        movimentoEstoqueRepository.save(movimento);
        produtoRepository.save(produto);
        tributacaoService.classificarProduto(produto);
    }

    @Transactional
    public void registrarSaida(Long produtoId, BigDecimal quantidade, String observacao) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        // --- CORREÇÃO DO ERRO DE TESTE (18 vs 0) ---
        // Se o produto foi cadastrado no teste apenas com 'quantidadeEmEstoque' mas sem os parciais,
        // inicializamos o estoque não fiscal para evitar erro de cálculo.
        int estoqueAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0;
        int fInicial = produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0;
        int nfInicial = produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0;

        if (estoqueAtual > 0 && (fInicial + nfInicial) == 0) {
            nfInicial = estoqueAtual;
            produto.setEstoqueNaoFiscal(nfInicial);
        }
        // -------------------------------------------

        if (estoqueAtual < quantidade.intValue()) {
            throw new RuntimeException("Estoque insuficiente para a saída.");
        }

        int saldoAnterior = estoqueAtual;
        int qtdBaixa = quantidade.intValue();

        // Prioridade de baixa: Fiscal -> Não Fiscal
        if (fInicial >= qtdBaixa) {
            produto.setEstoqueFiscal(fInicial - qtdBaixa);
        } else {
            int restante = qtdBaixa - fInicial;
            produto.setEstoqueFiscal(0);
            produto.setEstoqueNaoFiscal(Math.max(0, nfInicial - restante));
        }

        int novoFiscal = produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0;
        int novoNaoFiscal = produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0;
        produto.setQuantidadeEmEstoque(novoFiscal + novoNaoFiscal);

        baixarPorLote(produto, quantidade);

        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produto);
        movimento.setTipoMovimentoEstoque(TipoMovimentoEstoque.SAIDA);
        movimento.setMotivoMovimentacaoDeEstoque(MotivoMovimentacaoDeEstoque.VENDA);
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setQuantidadeMovimentada(quantidade);
        movimento.setSaldoAnterior(saldoAnterior);
        movimento.setSaldoAtual(produto.getQuantidadeEmEstoque());
        movimento.setDocumentoReferencia(observacao);

        movimentoEstoqueRepository.save(movimento);
        produtoRepository.save(produto);
    }

    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dados) {
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com código: " + dados.getCodigoBarras()));

        // --- CORREÇÃO DE SEGURANÇA PARA TESTES ---
        int saldoAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0;
        int f = produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0;
        int nf = produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0;

        if (saldoAtual > 0 && (f + nf) == 0) {
            nf = saldoAtual;
            produto.setEstoqueNaoFiscal(nf);
        }
        // -----------------------------------------

        TipoMovimentoEstoque tipo = dados.getMotivo().isEntrada()
                ? TipoMovimentoEstoque.ENTRADA
                : TipoMovimentoEstoque.SAIDA;

        int qtdAjuste = dados.getQuantidade().intValue();
        int saldoAnterior = saldoAtual;

        if (tipo == TipoMovimentoEstoque.ENTRADA) {
            produto.setEstoqueNaoFiscal(nf + qtdAjuste);
        } else {
            // Saída
            if (nf >= qtdAjuste) {
                produto.setEstoqueNaoFiscal(nf - qtdAjuste);
            } else {
                produto.setEstoqueNaoFiscal(0);
                int restante = qtdAjuste - nf;
                produto.setEstoqueFiscal(Math.max(0, f - restante));
            }
        }

        int novoFiscal = produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0;
        int novoNaoFiscal = produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0;
        produto.setQuantidadeEmEstoque(novoFiscal + novoNaoFiscal);

        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produto);
        movimento.setTipoMovimentoEstoque(tipo);
        movimento.setMotivoMovimentacaoDeEstoque(dados.getMotivo());
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setQuantidadeMovimentada(dados.getQuantidade());
        movimento.setSaldoAnterior(saldoAnterior);
        movimento.setSaldoAtual(produto.getQuantidadeEmEstoque());

        String obs = dados.getObservacao() != null ? dados.getObservacao() : "Ajuste Manual";
        movimento.setDocumentoReferencia(dados.getMotivo().name() + ": " + obs);

        movimentoEstoqueRepository.save(movimento);
        produtoRepository.save(produto);

        if (tipo == TipoMovimentoEstoque.SAIDA) {
            baixarPorLote(produto, dados.getQuantidade());
        }
    }

    private void registrarLote(Produto produto, EstoqueRequestDTO entrada) {
        LoteProduto lote = loteProdutoRepository.findByProdutoAndNumeroLote(produto, entrada.getNumeroLote())
                .orElse(new LoteProduto());

        if(lote.getId() == null) {
            lote.setProduto(produto);
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

    private void baixarPorLote(Produto produto, BigDecimal quantidadeABaixar) {
        List<LoteProduto> lotes = loteProdutoRepository.findLotesDisponiveis(produto);

        for (LoteProduto lote : lotes) {
            if (quantidadeABaixar.compareTo(BigDecimal.ZERO) <= 0) break;

            if (lote.getQuantidadeAtual().compareTo(quantidadeABaixar) >= 0) {
                lote.setQuantidadeAtual(lote.getQuantidadeAtual().subtract(quantidadeABaixar));
                quantidadeABaixar = BigDecimal.ZERO;
            } else {
                quantidadeABaixar = quantidadeABaixar.subtract(lote.getQuantidadeAtual());
                lote.setQuantidadeAtual(BigDecimal.ZERO);
            }
            loteProdutoRepository.save(lote);
        }
    }
}
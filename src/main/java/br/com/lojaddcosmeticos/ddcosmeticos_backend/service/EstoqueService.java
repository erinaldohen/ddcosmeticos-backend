package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor; // Importante
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoRepository;
    @Autowired private ProdutoService produtoService;

    @Transactional(readOnly = true)
    public List<Produto> gerarSugestaoCompras() {
        return produtoRepository.findProdutosComBaixoEstoque();
    }

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO dados) {
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + dados.getCodigoBarras()));

        Integer qtdEntrada = dados.getQuantidade().intValue();
        BigDecimal custoUnitario = dados.getPrecoCusto();

        produtoService.processarEntradaEstoque(produto, qtdEntrada, custoUnitario);

        if (dados.getNumeroNotaFiscal() != null && !dados.getNumeroNotaFiscal().isBlank()) {
            produto.setEstoqueFiscal(produto.getEstoqueFiscal() + qtdEntrada);
        } else {
            produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() + qtdEntrada);
        }

        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);

        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produto);
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setTipoMovimentoEstoque(TipoMovimentoEstoque.ENTRADA);
        movimento.setQuantidadeMovimentada(new BigDecimal(qtdEntrada));
        movimento.setCustoMovimentado(custoUnitario);
        movimento.setMotivoMovimentacaoDeEstoque(dados.getNumeroNotaFiscal() != null ?
                MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL : MotivoMovimentacaoDeEstoque.COMPRA_SEM_NOTA_FISCAL);
        movimento.setObservacao("NF: " + (dados.getNumeroNotaFiscal() != null ? dados.getNumeroNotaFiscal() : "S/N"));
        movimento.setDocumentoReferencia(dados.getNumeroNotaFiscal());
        movimento.setSaldoAtual(produto.getQuantidadeEmEstoque());

        movimentoRepository.save(movimento);
    }

    // --- NOVO MÉTODO PARA INTEGRAÇÃO COM PEDIDO DE COMPRA ---
    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario,
                                         Fornecedor fornecedor, String numeroNotaFiscal) {

        Integer qtd = quantidade.intValue();

        // 1. CÁLCULO FINANCEIRO (Preço Médio Ponderado)
        produtoService.processarEntradaEstoque(produto, qtd, custoUnitario);

        // 2. ATUALIZAÇÃO FÍSICA (Entrada de Pedido -> Fiscal)
        produto.setEstoqueFiscal(produto.getEstoqueFiscal() + qtd);

        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);

        // 3. REGISTRO DE HISTÓRICO
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produto);
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setTipoMovimentoEstoque(TipoMovimentoEstoque.ENTRADA);
        movimento.setQuantidadeMovimentada(quantidade);
        movimento.setCustoMovimentado(custoUnitario);
        movimento.setMotivoMovimentacaoDeEstoque(MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL);
        movimento.setObservacao("Recebimento Pedido | NF: " + numeroNotaFiscal);
        movimento.setDocumentoReferencia(numeroNotaFiscal);
        movimento.setFornecedor(fornecedor);
        movimento.setSaldoAtual(produto.getQuantidadeEmEstoque());

        movimentoRepository.save(movimento);
    }

    @Transactional
    public void registrarSaidaVenda(Produto produto, Integer quantidade) {
        if (produto.getEstoqueFiscal() >= quantidade) {
            produto.setEstoqueFiscal(produto.getEstoqueFiscal() - quantidade);
        } else {
            int restante = quantidade - produto.getEstoqueFiscal();
            produto.setEstoqueFiscal(0);
            produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() - restante);
        }

        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);

        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setTipoMovimentoEstoque(TipoMovimentoEstoque.SAIDA);
        mov.setQuantidadeMovimentada(new BigDecimal(quantidade));
        mov.setCustoMovimentado(produto.getPrecoMedioPonderado());
        mov.setMotivoMovimentacaoDeEstoque(MotivoMovimentacaoDeEstoque.VENDA);
        mov.setSaldoAtual(produto.getQuantidadeEmEstoque());

        movimentoRepository.save(mov);
    }

    @Transactional
    public void realizarAjusteManual(AjusteEstoqueDTO dados) {
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        int novaQuantidade = dados.getQuantidade().intValue();
        int diferenca = novaQuantidade - produto.getQuantidadeEmEstoque();

        if (diferenca > 0) {
            BigDecimal custoAjuste = produto.getPrecoMedioPonderado();
            produtoService.processarEntradaEstoque(produto, diferenca, custoAjuste);
            produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() + diferenca);
        } else {
            int qtdBaixa = Math.abs(diferenca);
            if (produto.getEstoqueNaoFiscal() >= qtdBaixa) {
                produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() - qtdBaixa);
            } else {
                int resta = qtdBaixa - produto.getEstoqueNaoFiscal();
                produto.setEstoqueNaoFiscal(0);
                produto.setEstoqueFiscal(produto.getEstoqueFiscal() - resta);
            }
        }

        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);

        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setDataMovimento(LocalDateTime.now());

        mov.setTipoMovimentoEstoque(diferenca > 0 ? TipoMovimentoEstoque.ENTRADA : TipoMovimentoEstoque.SAIDA);
        mov.setQuantidadeMovimentada(new BigDecimal(Math.abs(diferenca)));
        mov.setCustoMovimentado(produto.getPrecoMedioPonderado());

        mov.setMotivoMovimentacaoDeEstoque(diferenca > 0 ?
                MotivoMovimentacaoDeEstoque.AJUSTE_INVENTARIO_ENTRADA :
                MotivoMovimentacaoDeEstoque.AJUSTE_INVENTARIO_SAIDA);

        mov.setObservacao(dados.getObservacao());
        mov.setSaldoAtual(produto.getQuantidadeEmEstoque());

        movimentoRepository.save(mov);
    }
}
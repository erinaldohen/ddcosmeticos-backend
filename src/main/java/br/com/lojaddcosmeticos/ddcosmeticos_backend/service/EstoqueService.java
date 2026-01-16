package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private FornecedorRepository fornecedorRepository;

    @Transactional(readOnly = true)
    public List<Produto> gerarSugestaoCompras() {
        return produtoRepository.findProdutosComBaixoEstoque();
    }

    // --- ENTRADA VIA PEDIDO DE COMPRA (AUTOMÁTICA) ---
    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitarioNota,
                                         Fornecedor fornecedor, String numeroNotaFiscal) {

        BigDecimal qtdEntrada = quantidade; // Mantém BigDecimal para cálculo preciso

        // 1. Calcula o novo Preço Médio Ponderado
        BigDecimal novoPrecoMedio = calcularNovoPrecoMedio(produto, qtdEntrada, custoUnitarioNota);

        // Atualiza os preços do produto
        produto.setPrecoMedioPonderado(novoPrecoMedio);
        produto.setPrecoCusto(novoPrecoMedio); // O Custo oficial passa a ser a média

        // 2. Atualiza Saldos (Entrada de Pedido sempre é Fiscal)
        produto.setEstoqueFiscal(produto.getEstoqueFiscal() + qtdEntrada.intValue());
        produto.atualizarSaldoTotal();

        produtoRepository.save(produto);

        // 3. Registra Movimentação
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produto);
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setTipoMovimentoEstoque(TipoMovimentoEstoque.ENTRADA);
        movimento.setQuantidadeMovimentada(qtdEntrada);
        movimento.setCustoMovimentado(custoUnitarioNota); // No histórico fica o valor real pago na nota
        movimento.setMotivoMovimentacaoDeEstoque(MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL);
        movimento.setObservacao("Recebimento Pedido | NF: " + numeroNotaFiscal);
        movimento.setDocumentoReferencia(numeroNotaFiscal);
        movimento.setFornecedor(fornecedor);
        movimento.setSaldoAtual(produto.getQuantidadeEmEstoque());

        movimentoRepository.save(movimento);
    }

    // --- ENTRADA MANUAL (TELA DE ESTOQUE) ---
    @Transactional
    public void registrarEntrada(EstoqueRequestDTO dados) {
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + dados.getCodigoBarras()));

        BigDecimal qtdEntrada = dados.getQuantidade();
        BigDecimal custoUnitario = dados.getPrecoCusto();

        // 1. Calcula Média Ponderada (Lógica centralizada)
        BigDecimal novoPrecoMedio = calcularNovoPrecoMedio(produto, qtdEntrada, custoUnitario);
        produto.setPrecoMedioPonderado(novoPrecoMedio);
        produto.setPrecoCusto(novoPrecoMedio);

        // 2. Atualiza Saldos
        if (dados.getNumeroNotaFiscal() != null && !dados.getNumeroNotaFiscal().isBlank()) {
            produto.setEstoqueFiscal(produto.getEstoqueFiscal() + qtdEntrada.intValue());
        } else {
            produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() + qtdEntrada.intValue());
        }
        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);

        // 3. Registra Movimentação
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produto);
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setTipoMovimentoEstoque(TipoMovimentoEstoque.ENTRADA);
        movimento.setQuantidadeMovimentada(qtdEntrada);
        movimento.setCustoMovimentado(custoUnitario);
        movimento.setMotivoMovimentacaoDeEstoque(dados.getNumeroNotaFiscal() != null ?
                MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL : MotivoMovimentacaoDeEstoque.COMPRA_SEM_NOTA_FISCAL);
        movimento.setObservacao("NF: " + (dados.getNumeroNotaFiscal() != null ? dados.getNumeroNotaFiscal() : "S/N"));
        movimento.setDocumentoReferencia(dados.getNumeroNotaFiscal());
        movimento.setSaldoAtual(produto.getQuantidadeEmEstoque());
        movimentoRepository.save(movimento);

        gerarFinanceiroEntrada(dados, custoUnitario, qtdEntrada.intValue());
    }

    // --- MÉTODO AUXILIAR: CÁLCULO DE MÉDIA PONDERADA (EVITA DUPLICAÇÃO) ---
    private BigDecimal calcularNovoPrecoMedio(Produto produto, BigDecimal qtdEntrada, BigDecimal custoNovo) {
        BigDecimal estoqueAtual = new BigDecimal(produto.getQuantidadeEmEstoque());

        // Proteção contra nulos
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado() != null ?
                produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        // Se o estoque for zero ou negativo, a média é o próprio valor da entrada
        if (produto.getQuantidadeEmEstoque() <= 0) {
            return custoNovo;
        }

        BigDecimal valorTotalEstoqueAntigo = estoqueAtual.multiply(custoMedioAtual);
        BigDecimal valorTotalEntrada = qtdEntrada.multiply(custoNovo);
        BigDecimal novoEstoqueTotal = estoqueAtual.add(qtdEntrada);

        // Fórmula: (ValorTotalAntigo + ValorTotalEntrada) / NovoEstoqueTotal
        if (novoEstoqueTotal.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return valorTotalEstoqueAntigo.add(valorTotalEntrada)
                .divide(novoEstoqueTotal, 4, RoundingMode.HALF_UP);
    }

    private void gerarFinanceiroEntrada(EstoqueRequestDTO dados, BigDecimal custoUnitario, Integer qtd) {
        if (dados.getFormaPagamento() == null) return;

        BigDecimal valorTotal = custoUnitario.multiply(new BigDecimal(qtd));

        Fornecedor fornecedor = null;
        if (dados.getFornecedorCnpj() != null) {
            fornecedor = fornecedorRepository.findByCpfOuCnpj(dados.getFornecedorCnpj()).orElse(null);
        }

        int parcelas = dados.getQuantidadeParcelas() != null && dados.getQuantidadeParcelas() > 0 ? dados.getQuantidadeParcelas() : 1;
        BigDecimal valorParcela = valorTotal.divide(new BigDecimal(parcelas), 2, RoundingMode.HALF_UP);

        for (int i = 1; i <= parcelas; i++) {
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor);
            conta.setValorTotal(valorParcela);
            conta.setCategoria("COMPRA MERCADORIA");
            conta.setDescricao("Compra ref. " + (dados.getNumeroNotaFiscal() != null ? dados.getNumeroNotaFiscal() : "Estoque") + " - Parc " + i + "/" + parcelas);
            conta.setDataEmissao(LocalDate.now());

            if (dados.getFormaPagamento() == FormaDePagamento.DINHEIRO ||
                    dados.getFormaPagamento() == FormaDePagamento.PIX ||
                    dados.getFormaPagamento() == FormaDePagamento.DEBITO) {
                conta.setDataVencimento(LocalDate.now());
                conta.setDataPagamento(LocalDate.now());
                conta.setStatus(StatusConta.PAGO);
            } else {
                LocalDate vencto = dados.getDataVencimentoBoleto() != null ?
                        dados.getDataVencimentoBoleto() :
                        LocalDate.now().plusDays(30L * i);
                conta.setDataVencimento(vencto);
                conta.setStatus(StatusConta.PENDENTE);
            }
            contaPagarRepository.save(conta);
        }
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
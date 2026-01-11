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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoRepository;
    @Autowired private ProdutoService produtoService; // Use se existir lógica lá, senão implemente aqui

    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private FornecedorRepository fornecedorRepository;

    @Transactional(readOnly = true)
    public List<Produto> gerarSugestaoCompras() {
        return produtoRepository.findProdutosComBaixoEstoque();
    }

    // --- NOVO MÉTODO (CORREÇÃO DO ERRO) ---
    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario,
                                         Fornecedor fornecedor, String numeroNotaFiscal) {

        Integer qtdEntrada = quantidade.intValue();

        // 1. Atualiza Preço Médio e Custo
        if (produto.getQuantidadeEmEstoque() + qtdEntrada > 0) {
            BigDecimal valorAtual = produto.getPrecoMedioPonderado().multiply(new BigDecimal(produto.getQuantidadeEmEstoque()));
            BigDecimal valorEntrada = custoUnitario.multiply(quantidade);

            // Evita divisão por zero
            BigDecimal divisor = new BigDecimal(produto.getQuantidadeEmEstoque() + qtdEntrada);
            BigDecimal novoPrecoMedio = valorAtual.add(valorEntrada).divide(divisor, 4, java.math.RoundingMode.HALF_UP);

            produto.setPrecoMedioPonderado(novoPrecoMedio);
        } else {
            // Se estoque estava negativo e agora zera ou fica positivo, o preço médio é o da entrada
            produto.setPrecoMedioPonderado(custoUnitario);
        }

        produto.setPrecoCusto(custoUnitario);

        // 2. Atualiza Saldos (Entrada de Pedido sempre é Fiscal)
        produto.setEstoqueFiscal(produto.getEstoqueFiscal() + qtdEntrada);
        produto.atualizarSaldoTotal();

        produtoRepository.save(produto);

        // 3. Registra Movimentação
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produto);
        movimento.setDataMovimento(LocalDateTime.now());
        movimento.setTipoMovimentoEstoque(TipoMovimentoEstoque.ENTRADA);
        movimento.setQuantidadeMovimentada(quantidade);
        movimento.setCustoMovimentado(custoUnitario);
        movimento.setMotivoMovimentacaoDeEstoque(MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL);
        movimento.setObservacao("Recebimento Pedido | NF: " + numeroNotaFiscal);
        movimento.setDocumentoReferencia(numeroNotaFiscal);
        movimento.setFornecedor(fornecedor); // Associa o fornecedor
        movimento.setSaldoAtual(produto.getQuantidadeEmEstoque());

        movimentoRepository.save(movimento);
    }
    // --------------------------------------

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO dados) {
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + dados.getCodigoBarras()));

        Integer qtdEntrada = dados.getQuantidade().intValue();
        BigDecimal custoUnitario = dados.getPrecoCusto();

        // Reutiliza a lógica se possível ou mantém duplicado por simplicidade agora
        if (produto.getQuantidadeEmEstoque() + qtdEntrada > 0) {
            BigDecimal valorAtual = produto.getPrecoMedioPonderado().multiply(new BigDecimal(produto.getQuantidadeEmEstoque()));
            BigDecimal valorEntrada = custoUnitario.multiply(new BigDecimal(qtdEntrada));
            BigDecimal novoPrecoMedio = valorAtual.add(valorEntrada).divide(new BigDecimal(produto.getQuantidadeEmEstoque() + qtdEntrada), 4, java.math.RoundingMode.HALF_UP);
            produto.setPrecoMedioPonderado(novoPrecoMedio);
        }
        produto.setPrecoCusto(custoUnitario);

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

        gerarFinanceiroEntrada(dados, custoUnitario, qtdEntrada);
    }

    private void gerarFinanceiroEntrada(EstoqueRequestDTO dados, BigDecimal custoUnitario, Integer qtd) {
        if (dados.getFormaPagamento() == null) return;

        BigDecimal valorTotal = custoUnitario.multiply(new BigDecimal(qtd));

        Fornecedor fornecedor = null;
        if (dados.getFornecedorCnpj() != null) {
            // Ajuste para usar o método correto do repositório se necessário (findByCnpjCpf ou findByCpfOuCnpj)
            fornecedor = fornecedorRepository.findByCpfOuCnpj(dados.getFornecedorCnpj()).orElse(null);
        }

        int parcelas = dados.getQuantidadeParcelas() != null && dados.getQuantidadeParcelas() > 0 ? dados.getQuantidadeParcelas() : 1;
        BigDecimal valorParcela = valorTotal.divide(new BigDecimal(parcelas), 2, java.math.RoundingMode.HALF_UP);

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
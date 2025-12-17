package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusSugestao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private ConfiguracaoLojaRepository configuracaoLojaRepository;
    @Autowired private SugestaoPrecoRepository sugestaoPrecoRepository;

    // A injeção de FormaPagamento foi removida pois causava erro fatal no Spring

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO entrada) {
        Fornecedor fornecedor = buscarOuCriarFornecedor(entrada);

        Produto produto = produtoRepository.findByCodigoBarras(entrada.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + entrada.getCodigoBarras()));

        atualizarEstoqueECusto(produto, entrada.getQuantidade(), entrada.getPrecoCusto());
        registrarMovimento(produto, fornecedor, entrada);
        registrarContaPagar(entrada, fornecedor);
        verificarNecessidadeReajuste(produto, entrada.getPrecoCusto());
    }

    private Fornecedor buscarOuCriarFornecedor(EstoqueRequestDTO entrada) {
        return fornecedorRepository.findByCpfOuCnpj(entrada.getFornecedorCnpj())
                .orElseGet(() -> {
                    Fornecedor novo = new Fornecedor();
                    novo.setCpfOuCnpj(entrada.getFornecedorCnpj());
                    novo.setRazaoSocial("Fornecedor " + entrada.getFornecedorCnpj());
                    novo.setAtivo(true);
                    return fornecedorRepository.save(novo);
                });
    }

    private void atualizarEstoqueECusto(Produto produto, BigDecimal qtdEntrada, BigDecimal custoEntrada) {
        // CORREÇÃO: Uso de getQuantidadeEstoque (padrão)
        BigDecimal valorEstoqueAtual = produto.getQuantidadeEmEstoque().multiply(produto.getPrecoMedioPonderado());
        BigDecimal valorEntrada = qtdEntrada.multiply(custoEntrada);

        BigDecimal novaQuantidade = produto.getQuantidadeEmEstoque().add(qtdEntrada);

        if (novaQuantidade.compareTo(BigDecimal.ZERO) == 0) {
            produto.setQuantidadeEmEstoque(BigDecimal.ZERO);
            return;
        }

        BigDecimal novoPmp = valorEstoqueAtual.add(valorEntrada)
                .divide(novaQuantidade, 4, RoundingMode.HALF_UP);

        produto.setQuantidadeEmEstoque(novaQuantidade);
        produto.setPrecoMedioPonderado(novoPmp);

        if (produto.getPrecoCustoInicial().compareTo(BigDecimal.ZERO) == 0) {
            produto.setPrecoCustoInicial(custoEntrada);
        }

        produtoRepository.save(produto);
    }

    private void registrarMovimento(Produto produto, Fornecedor fornecedor, EstoqueRequestDTO entrada) {
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setFornecedor(fornecedor);
        mov.setTipoMovimento("ENTRADA");
        mov.setQuantidadeMovimentada(entrada.getQuantidade());
        mov.setCustoMovimentado(entrada.getPrecoCusto());
        mov.setDataMovimento(LocalDateTime.now());
        movimentoEstoqueRepository.save(mov);
    }

    private void registrarContaPagar(EstoqueRequestDTO entrada, Fornecedor fornecedor) {
        BigDecimal valorTotal = entrada.getQuantidade().multiply(entrada.getPrecoCusto());
        int parcelas = (entrada.getQuantidadeParcelas() != null && entrada.getQuantidadeParcelas() > 0)
                ? entrada.getQuantidadeParcelas() : 1;

        if (entrada.getFormaPagamento() == FormaPagamento.DINHEIRO ||
                entrada.getFormaPagamento() == FormaPagamento.PIX ||
                entrada.getFormaPagamento() == FormaPagamento.DEBITO) {
            parcelas = 1;
        }

        BigDecimal valorParcela = valorTotal.divide(new BigDecimal(parcelas), 2, RoundingMode.HALF_UP);

        for (int i = 1; i <= parcelas; i++) {
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor);
            conta.setDataEmissao(LocalDate.now());

            if (parcelas > 1) {
                conta.setDescricao("Compra Estoque - NF " + entrada.getNumeroNotaFiscal() + " (Parc " + i + "/" + parcelas + ")");
                conta.setValorTotal(valorParcela);
            } else {
                conta.setDescricao("Compra Estoque - NF " + entrada.getNumeroNotaFiscal());
                conta.setValorTotal(valorTotal);
            }

            if (entrada.getFormaPagamento() == FormaPagamento.DINHEIRO ||
                    entrada.getFormaPagamento() == FormaPagamento.PIX ||
                    entrada.getFormaPagamento() == FormaPagamento.DEBITO) {

                conta.setStatus(StatusConta.PAGO);
                conta.setDataPagamento(LocalDate.now());
                conta.setDataVencimento(LocalDate.now());

            } else {
                conta.setStatus(StatusConta.PENDENTE);
                if (entrada.getFormaPagamento() == FormaPagamento.BOLETO && entrada.getDataVencimentoBoleto() != null) {
                    conta.setDataVencimento(entrada.getDataVencimentoBoleto());
                } else {
                    conta.setDataVencimento(LocalDate.now().plusDays(30L * i));
                }
            }
            contaPagarRepository.save(conta);
        }
    }

    private void verificarNecessidadeReajuste(Produto produto, BigDecimal novoCusto) {
        ConfiguracaoLoja config = configuracaoLojaRepository.findAll().stream().findFirst().orElse(null);
        if (config == null) return;

        BigDecimal precoVendaAtual = produto.getPrecoVenda();
        if (precoVendaAtual.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal lucroBruto = precoVendaAtual.subtract(novoCusto);
        BigDecimal margemAtualPercentual = lucroBruto.divide(precoVendaAtual, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        if (margemAtualPercentual.compareTo(config.getMargemLucroAlvo()) < 0) {
            BigDecimal divisor = BigDecimal.ONE.subtract(
                    config.getMargemLucroAlvo().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));

            BigDecimal precoSugerido = novoCusto.divide(divisor, 2, RoundingMode.CEILING);

            SugestaoPreco sugestao = new SugestaoPreco();
            sugestao.setProduto(produto);
            sugestao.setCustoAntigo(produto.getPrecoMedioPonderado());
            sugestao.setCustoNovo(novoCusto);
            sugestao.setPrecoVendaAtual(precoVendaAtual);
            sugestao.setPrecoVendaSugerido(precoSugerido);
            sugestao.setMargemAtual(margemAtualPercentual);
            sugestao.setMargemProjetada(config.getMargemLucroAlvo());
            sugestao.setDataGeracao(LocalDateTime.now());
            sugestao.setStatus(StatusSugestao.PENDENTE);
            sugestao.setMotivo("Margem abaixo da meta de " + config.getMargemLucroAlvo() + "%");

            sugestaoPrecoRepository.save(sugestao);
        }
    }

    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario, Fornecedor fornecedor, String numeroNotaFiscal) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(produto.getCodigoBarras());
        dto.setQuantidade(quantidade);
        dto.setPrecoCusto(custoUnitario);
        dto.setNumeroNotaFiscal(numeroNotaFiscal);

        if (fornecedor != null) {
            dto.setFornecedorCnpj(fornecedor.getCpfOuCnpj());
        }

        dto.setFormaPagamento(FormaPagamento.BOLETO);
        dto.setQuantidadeParcelas(1);
        dto.setDataVencimentoBoleto(LocalDate.now().plusDays(30));

        registrarEntrada(dto);
    }

    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dto) {
        Produto produto = produtoRepository.findByCodigoBarras(dto.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + dto.getCodigoBarras()));

        if ("ENTRADA".equalsIgnoreCase(dto.getTipoMovimento()) || "SOBRA".equalsIgnoreCase(dto.getTipoMovimento())) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().add(dto.getQuantidade()));
        } else {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().subtract(dto.getQuantidade()));
        }

        produtoRepository.save(produto);

        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setQuantidadeMovimentada(dto.getQuantidade());
        mov.setTipoMovimento(dto.getTipoMovimento().toUpperCase());
        mov.setCustoMovimentado(produto.getPrecoMedioPonderado());
        mov.setFornecedor(null);

        movimentoEstoqueRepository.save(mov);
    }
}
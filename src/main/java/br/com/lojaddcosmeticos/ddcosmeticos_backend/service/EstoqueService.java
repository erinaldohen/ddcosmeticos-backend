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
    @Autowired private TributacaoService tributacaoService;
    @Autowired private AuditoriaRepository auditoriaRepository;

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO entrada) {
        Fornecedor fornecedor = buscarOuCriarFornecedor(entrada);
        Produto produto = produtoRepository.findByCodigoBarras(entrada.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + entrada.getCodigoBarras()));

        atualizarEstoqueECusto(produto, entrada.getQuantidade(), entrada.getPrecoCusto());
        tributacaoService.classificarProduto(produto);
        registrarMovimento(produto, fornecedor, entrada);
        registrarContaPagar(entrada, fornecedor);
        verificarNecessidadeReajuste(produto, entrada.getPrecoCusto());

        if ("FISICA".equalsIgnoreCase(fornecedor.getTipoPessoa())) {
            gerarAuditoriaNotaAvulsa(fornecedor, entrada);
        }
    }

    private void gerarAuditoriaNotaAvulsa(Fornecedor fornecedor, EstoqueRequestDTO entrada) {
        Auditoria log = new Auditoria();
        log.setDataHora(LocalDateTime.now());
        log.setTipoEvento("EMISSAO_NOTA_ENTRADA");
        log.setEntidadeAfetada("Estoque");
        log.setMensagem("NOTA DE ENTRADA (CPF) GERADA para: " + fornecedor.getNomeFantasia());
        log.setUsuarioResponsavel("SISTEMA");
        auditoriaRepository.save(log);
    }

    private Fornecedor buscarOuCriarFornecedor(EstoqueRequestDTO entrada) {
        String docLimpo = entrada.getFornecedorCnpj().replaceAll("\\D", "");
        return fornecedorRepository.findByCpfOuCnpj(docLimpo)
                .orElseGet(() -> {
                    Fornecedor novo = new Fornecedor();
                    novo.setCpfOuCnpj(docLimpo);
                    novo.setRazaoSocial("Fornecedor " + docLimpo);
                    novo.setNomeFantasia("Fornecedor " + docLimpo);
                    novo.setAtivo(true);
                    novo.setTipoPessoa(docLimpo.length() <= 11 ? "FISICA" : "JURIDICA");
                    return fornecedorRepository.save(novo);
                });
    }

    private void atualizarEstoqueECusto(Produto produto, BigDecimal qtdEntrada, BigDecimal custoEntrada) {
        BigDecimal estoqueAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;
        BigDecimal pmpAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        BigDecimal valorEstoqueAtual = estoqueAtual.multiply(pmpAtual);
        BigDecimal valorEntrada = qtdEntrada.multiply(custoEntrada);
        BigDecimal novaQuantidade = estoqueAtual.add(qtdEntrada);

        if (novaQuantidade.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal novoPmp = valorEstoqueAtual.add(valorEntrada)
                    .divide(novaQuantidade, 4, RoundingMode.HALF_UP);
            produto.setPrecoMedioPonderado(novoPmp);
        }

        produto.setQuantidadeEmEstoque(novaQuantidade);
        if (produto.getPrecoCustoInicial() == null || produto.getPrecoCustoInicial().compareTo(BigDecimal.ZERO) == 0) {
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
        int parcelas = (entrada.getQuantidadeParcelas() != null && entrada.getQuantidadeParcelas() > 0) ? entrada.getQuantidadeParcelas() : 1;

        if (entrada.getFormaPagamento() == FormaPagamento.DINHEIRO ||
                entrada.getFormaPagamento() == FormaPagamento.PIX ||
                entrada.getFormaPagamento() == FormaPagamento.DEBITO) {
            parcelas = 1;
        }

        BigDecimal valorParcela = valorTotal.divide(new BigDecimal(parcelas), 2, RoundingMode.HALF_UP);

        for (int i = 1; i <= parcelas; i++) {
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor); // LINHA 142: Agora compila perfeitamente!
            conta.setDataEmissao(LocalDate.now());
            conta.setCategoria("COMPRA_ESTOQUE");

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
        configuracaoLojaRepository.findAll().stream().findFirst().ifPresent(config -> {
            BigDecimal precoVendaAtual = produto.getPrecoVenda();
            if (precoVendaAtual == null || precoVendaAtual.compareTo(BigDecimal.ZERO) == 0) return;

            BigDecimal lucroBruto = precoVendaAtual.subtract(novoCusto);
            BigDecimal margemAtualPercentual = lucroBruto.divide(precoVendaAtual, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

            if (margemAtualPercentual.compareTo(config.getMargemLucroAlvo()) < 0) {
                BigDecimal divisor = BigDecimal.ONE.subtract(config.getMargemLucroAlvo().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
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

        dto.setFormaPagamento(FormaPagamento.BOLETO);
        dto.setQuantidadeParcelas(1);
        dto.setDataVencimentoBoleto(LocalDate.now().plusDays(30));

        registrarEntrada(dto);
    }

    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dto) {
        Produto produto = produtoRepository.findByCodigoBarras(dto.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        if ("ENTRADA".equalsIgnoreCase(dto.getTipoMovimento()) || "SOBRA".equalsIgnoreCase(dto.getTipoMovimento())) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().add(dto.getQuantidade()));
        } else {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().subtract(dto.getQuantidade()));
        }
        produtoRepository.save(produto);

        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setDataMovimento(LocalDateTime.now()); // Ajustado para o campo correto
        mov.setQuantidadeMovimentada(dto.getQuantidade());
        mov.setTipoMovimento(dto.getTipoMovimento().toUpperCase());
        mov.setCustoMovimentado(produto.getPrecoMedioPonderado());
        movimentoEstoqueRepository.save(mov);
    }

    /**
     * Realiza a baixa física do produto no estoque durante a venda.
     */
    @Transactional
    public void realizarAjusteSaidaVenda(Produto produto, BigDecimal quantidade) {
        // Garante que o valor atual não seja nulo antes da conta
        BigDecimal estoqueAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;

        // Atualiza a entidade
        produto.setQuantidadeEmEstoque(estoqueAtual.subtract(quantidade));
        produtoRepository.save(produto);

        // Registra o movimento para auditoria e relatórios (Curva ABC)
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setTipoMovimento("SAIDA_VENDA");
        mov.setQuantidadeMovimentada(quantidade);
        mov.setDataMovimento(LocalDateTime.now());
        // Se o seu MovimentoEstoque tiver custoMovimentado, salve o custo médio do produto aqui
        mov.setCustoMovimentado(produto.getPrecoMedioPonderado());

        movimentoEstoqueRepository.save(mov);
    }

    /**
     * Devolve o produto ao estoque em caso de cancelamento ou estorno.
     */
    @Transactional
    public void estornarEstoqueVenda(Produto produto, BigDecimal quantidade, String motivo) {
        BigDecimal estoqueAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;
        produto.setQuantidadeEmEstoque(estoqueAtual.add(quantidade));
        produtoRepository.save(produto);

        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setTipoMovimento(motivo); // ex: "CANCELAMENTO_VENDA"
        mov.setQuantidadeMovimentada(quantidade);
        mov.setDataMovimento(LocalDateTime.now());
        movimentoEstoqueRepository.save(mov);
    }
}
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
    @Autowired private TributacaoService tributacaoService;
    @Autowired private AuditoriaRepository auditoriaRepository;

    /**
     * Recupera o usuário logado no contexto de segurança.
     * Retorna null se for uma operação automática do sistema.
     */
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

        // --- REGRA FISCAL ---
        // Se houver nota fiscal na entrada, marcamos o produto para permitir emissão parcial de NFC-e futuramente
        if (entrada.getNumeroNotaFiscal() != null && !entrada.getNumeroNotaFiscal().isBlank()) {
            produto.setPossuiNfEntrada(true);
        }

        atualizarEstoqueECusto(produto, entrada.getQuantidade(), entrada.getPrecoCusto());

        // Linha 120 (Correção): Garante que o tributacaoService não é nulo e trata possíveis erros de classificação
        try {
            if(tributacaoService != null) {
                tributacaoService.classificarProduto(produto);
            }
        } catch (Exception e) {
            log.warn("Erro ao classificar tributação do produto {}: {}", produto.getCodigoBarras(), e.getMessage());
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
            if ("ENTRADA".equalsIgnoreCase(dto.getTipoMovimento())) return;
            throw new ValidationException("Motivo de ajuste inválido: " + dto.getMotivo());
        }

        TipoMovimentoEstoque status = switch (motivo) {
            case AJUSTE_SOBRA, DEVOLUCAO_CLIENTE, CANCELAMENTO_DE_VENDA, COMPRA_FORNECEDOR -> TipoMovimentoEstoque.ENTRADA;
            case AJUSTE_PERDA, AJUSTE_AVARIA, USO_INTERNO, VENDA, DEVOLUCAO_AO_FORNECEDOR -> TipoMovimentoEstoque.SAIDA;
            default -> throw new ValidationException("Motivo não mapeado para movimentação de estoque: " + motivo);
        };

        if (status == TipoMovimentoEstoque.ENTRADA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().add(dto.getQuantidade()));
        } else {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().subtract(dto.getQuantidade()));
        }
        produtoRepository.save(produto);

        registrarMovimento(produto, null, dto.getQuantidade(), produto.getPrecoMedioPonderado(), status, motivo);
    }

    private void atualizarEstoqueECusto(Produto produto, BigDecimal qtdEntrada, BigDecimal custoEntrada) {
        BigDecimal estoqueAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;
        BigDecimal pmpAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        BigDecimal valorEstoqueAtual = estoqueAtual.multiply(pmpAtual);
        BigDecimal valorEntrada = qtdEntrada.multiply(custoEntrada);
        BigDecimal novaQuantidade = estoqueAtual.add(qtdEntrada);

        if (novaQuantidade.compareTo(BigDecimal.ZERO) == 0) {
            produto.setQuantidadeEmEstoque(BigDecimal.ZERO);
            return;
        }

        BigDecimal novoPmp = valorEstoqueAtual.add(valorEntrada)
                .divide(novaQuantidade, 4, RoundingMode.HALF_UP);

        produto.setQuantidadeEmEstoque(novaQuantidade);
        produto.setPrecoMedioPonderado(novoPmp);

        if (produto.getPrecoCustoInicial() == null || produto.getPrecoCustoInicial().compareTo(BigDecimal.ZERO) == 0) {
            produto.setPrecoCustoInicial(custoEntrada);
        }
        produtoRepository.save(produto);
    }

    // Linha 173/184: Correção do uso do Enum FormaDePagamento (Antes estava FormaPagamento)
    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario, Fornecedor fornecedor, String numeroNotaFiscal) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(produto.getCodigoBarras());
        dto.setQuantidade(quantidade);
        dto.setPrecoCusto(custoUnitario);
        dto.setNumeroNotaFiscal(numeroNotaFiscal);
        if (fornecedor != null) dto.setFornecedorCnpj(fornecedor.getCpfOuCnpj());

        // FIX: Usando o enum correto FormaDePagamento
        dto.setFormaPagamento(FormaDePagamento.BOLETO);
        dto.setQuantidadeParcelas(1);
        dto.setDataVencimentoBoleto(LocalDate.now().plusDays(30));

        registrarEntrada(dto);
    }

    private void registrarContaPagar(EstoqueRequestDTO entrada, Fornecedor fornecedor) {
        BigDecimal valorTotal = entrada.getQuantidade().multiply(entrada.getPrecoCusto());
        int parcelas = (entrada.getQuantidadeParcelas() != null && entrada.getQuantidadeParcelas() > 0) ? entrada.getQuantidadeParcelas() : 1;

        // Linha 200 e 206: Correção na chamada do método auxiliar
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

            // Categoria contábil padrão para compras de estoque
            conta.setCategoria("FORNECEDORES_MERCADORIA");

            // Linha 216 e 217: Correção de Enum e uso de data de vencimento
            if (isPagamentoAVista(entrada.getFormaPagamento())) {
                conta.setStatus(StatusConta.PAGO);
                conta.setDataPagamento(LocalDate.now());
                conta.setDataVencimento(LocalDate.now());
            } else {
                conta.setStatus(StatusConta.PENDENTE);
                // FIX: Enum correto
                if (entrada.getFormaPagamento() == FormaDePagamento.BOLETO && entrada.getDataVencimentoBoleto() != null) {
                    conta.setDataVencimento(entrada.getDataVencimentoBoleto());
                } else {
                    conta.setDataVencimento(LocalDate.now().plusDays(30L * i));
                }
            }
            contaPagarRepository.save(conta);
        }
    }

    // FIX: Assinatura do método corrigida para receber FormaDePagamento
    private boolean isPagamentoAVista(FormaDePagamento fp) {
        return fp == FormaDePagamento.DINHEIRO || fp == FormaDePagamento.PIX || fp == FormaDePagamento.DEBITO;
    }

    private void verificarNecessidadeReajuste(Produto produto, BigDecimal novoCusto) {
        ConfiguracaoLoja config = configuracaoLojaRepository.findAll().stream().findFirst().orElse(null);
        if (config == null || produto.getPrecoVenda() == null || produto.getPrecoVenda().compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal lucroBruto = produto.getPrecoVenda().subtract(novoCusto);
        BigDecimal margemAtualPercentual = lucroBruto.divide(produto.getPrecoVenda(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

        if (margemAtualPercentual.compareTo(config.getMargemLucroAlvo()) < 0) {
            BigDecimal divisor = BigDecimal.ONE.subtract(config.getMargemLucroAlvo().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            BigDecimal precoSugerido = novoCusto.divide(divisor, 2, RoundingMode.CEILING);

            // Linha 231: Validação de nulidade antes de buscar/salvar
            boolean existePendente = sugestaoPrecoRepository.existsByProdutoAndStatus(produto, StatusPrecificacao.PENDENTE);
            if (!existePendente) {
                SugestaoPreco sugestao = new SugestaoPreco();
                sugestao.setProduto(produto);
                sugestao.setCustoAntigo(produto.getPrecoMedioPonderado());
                sugestao.setCustoNovo(novoCusto);
                sugestao.setPrecoVendaAtual(produto.getPrecoVenda());
                sugestao.setPrecoVendaSugerido(precoSugerido);
                sugestao.setMargemAtual(margemAtualPercentual);
                sugestao.setMargemProjetada(config.getMargemLucroAlvo());
                sugestao.setDataGeracao(LocalDateTime.now());
                sugestao.setStatusPrecificacao(StatusPrecificacao.PENDENTE);
                sugestao.setMotivo("Margem caiu para " + margemAtualPercentual + "%. Meta: " + config.getMargemLucroAlvo() + "%");

                sugestaoPrecoRepository.save(sugestao);
            }
        }
    }

    private void gerarAuditoriaNotaAvulsa(Fornecedor fornecedor) {
        Auditoria logAuditoria = new Auditoria();
        logAuditoria.setDataHora(LocalDateTime.now());
        logAuditoria.setTipoEvento("EMISSAO_NOTA_ENTRADA");
        logAuditoria.setEntidadeAfetada("Estoque");

        // Linha 242: Garantia de que mensagem e usuário não quebrem
        logAuditoria.setMensagem("NOTA DE ENTRADA (CPF) PROCESSADA para " + (fornecedor != null ? fornecedor.getNomeFantasia() : "DESCONHECIDO"));

        Usuario u = getUsuarioLogado();
        // Assume-se que Auditoria espera uma String no campo usuarioResponsavel.
        // Se sua entidade Auditoria esperar um objeto Usuario, altere para: logAuditoria.setUsuario(u);
        logAuditoria.setUsuarioResponsavel(u != null ? u.getNome() : "SISTEMA_AUTOMATICO");

        auditoriaRepository.save(logAuditoria);
    }

    private Fornecedor buscarOuCriarFornecedor(EstoqueRequestDTO entrada) {
        String docOriginal = entrada.getFornecedorCnpj();
        if(docOriginal == null) docOriginal = "";

        String docLimpo = docOriginal.replaceAll("\\D", "");

        String finalDocOriginal = docOriginal;
        return fornecedorRepository.findByCpfOuCnpj(docOriginal)
                .or(() -> fornecedorRepository.findByCpfOuCnpj(docLimpo))
                .orElseGet(() -> {
                    Fornecedor novo = new Fornecedor();
                    novo.setCpfOuCnpj(finalDocOriginal);
                    novo.setRazaoSocial("Fornecedor " + finalDocOriginal);
                    novo.setNomeFantasia("Fornecedor " + finalDocOriginal);
                    novo.setAtivo(true);
                    novo.setTipoPessoa(docLimpo.length() <= 11 ? "FISICA" : "JURIDICA");
                    return fornecedorRepository.save(novo);
                });
    }
}
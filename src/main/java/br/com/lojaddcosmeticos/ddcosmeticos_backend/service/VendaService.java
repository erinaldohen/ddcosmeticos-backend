package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import org.hibernate.Hibernate;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VendaService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ConfiguracaoLojaService configuracaoLojaService;
    @Autowired private RegraTributariaRepository regraTributariaRepository;
    @Autowired private CaixaDiarioRepository caixaRepository;
    @Autowired private EstoqueService estoqueService;
    @Autowired private FinanceiroService financeiroService;
    @Autowired private NfceService nfceService;
    @Autowired private CalculadoraFiscalService calculadoraFiscalService;
    @Autowired private AuditoriaService auditoriaService;

    // =========================================================================
    // 1. PROCESSAMENTO DE VENDAS (FINALIZAÇÃO)
    // =========================================================================

    @Transactional
    public VendaResponseDTO realizarVenda(VendaRequestDTO dto) {
        // 1. Validações Iniciais
        Usuario usuarioLogado = capturarUsuarioLogado();
        if (usuarioLogado == null) throw new ValidationException("Sessão inválida ou expirada.");

        CaixaDiario caixa = validarCaixaAberto(usuarioLogado);

        // 2. Validação de Pagamentos
        if (dto.pagamentos() == null || dto.pagamentos().isEmpty()) {
            throw new ValidationException("É necessário informar ao menos uma forma de pagamento.");
        }

        // 3. Montagem da Venda
        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setDataVenda(LocalDateTime.now());
        venda.setCaixa(caixa);

        // 4. Vínculo do Cliente
        if (dto.clienteId() != null) {
            Cliente cliente = clienteRepository.findById(dto.clienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado ID: " + dto.clienteId()));
            venda.setCliente(cliente);
            venda.setClienteNome(cliente.getNome());
            venda.setClienteDocumento(cliente.getDocumento());
        } else {
            venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Consumidor Final");
            venda.setClienteDocumento(dto.clienteDocumento());
        }

        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);
        RegraTributaria regra = buscarRegraVigente();

        // 5. Processamento dos Itens
        List<ItemVenda> itens = dto.itens().stream().map(itemDto -> {
            Produto produto = produtoRepository.findById(itemDto.produtoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado ID: " + itemDto.produtoId()));

            processarSaidaEstoqueComAuditoria(produto, itemDto.quantidade().intValue());

            ItemVenda item = new ItemVenda();
            item.setVenda(venda);
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(itemDto.precoUnitario());
            item.setDesconto(itemDto.desconto() != null ? itemDto.desconto() : BigDecimal.ZERO);

            item.setCustoUnitarioHistorico(nvl(produto.getPrecoMedioPonderado()));
            item.setAliquotaIbsAplicada(regra.getAliquotaIbs());
            item.setAliquotaCbsAplicada(regra.getAliquotaCbs());

            return item;
        }).collect(Collectors.toList());

        venda.setItens(itens);

        // 6. Cálculos de Totais
        BigDecimal subtotalItens = itens.stream()
                .map(i -> (i.getPrecoUnitario().multiply(i.getQuantidade())).subtract(i.getDesconto()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorFinal = subtotalItens.subtract(venda.getDescontoTotal()).max(BigDecimal.ZERO);
        venda.setValorTotal(valorFinal);

        // 7. Validação Financeira
        BigDecimal totalPago = dto.pagamentos().stream()
                .map(PagamentoRequestDTO::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPago.compareTo(valorFinal) < 0 && totalPago.subtract(valorFinal).abs().compareTo(new BigDecimal("0.05")) > 0) {
            throw new ValidationException("O valor pago (R$ " + totalPago + ") é menor que o total da venda (R$ " + valorFinal + ").");
        }

        // 8. Processamento dos Pagamentos
        List<PagamentoVenda> pagamentos = dto.pagamentos().stream().map(pgDto -> {
            PagamentoVenda pg = new PagamentoVenda();
            pg.setVenda(venda);
            pg.setFormaPagamento(pgDto.formaPagamento());
            pg.setValor(pgDto.valor());
            pg.setParcelas(pgDto.parcelas() != null ? pgDto.parcelas() : 1);
            return pg;
        }).collect(Collectors.toList());

        venda.setPagamentos(pagamentos);

        if (!pagamentos.isEmpty()) {
            venda.setFormaDePagamento(pagamentos.get(0).getFormaPagamento());
        }

        venda.setStatusNfce(StatusFiscal.PENDENTE);

        BigDecimal totalDescontosConcedidos = venda.getDescontoTotal()
                .add(itens.stream().map(ItemVenda::getDesconto).reduce(BigDecimal.ZERO, BigDecimal::add));
        validarLimitesDeDesconto(usuarioLogado, subtotalItens, totalDescontosConcedidos);
        aplicarDadosFiscais(venda, itens);

        // 9. Persistência
        vendaRepository.save(venda);

        // 10. Atualiza Financeiro (Caixa)
        atualizarFinanceiro(caixa, venda, pagamentos);

        // 11. Emissão NFC-e Assíncrona
        if (!Boolean.TRUE.equals(dto.ehOrcamento())) {
            // Despacha a emissão para uma thread secundária (Fire and Forget)
            CompletableFuture.runAsync(() -> {
                try {
                    nfceService.emitirNfce(venda);
                } catch (Exception e) {
                    // O erro é registado, mas o cliente já foi despachado no PDV.
                    // O NfceScheduler vai tentar reprocessar esta nota no próximo minuto.
                    log.error("Erro na emissão assíncrona de NFC-e para venda {}: {}", venda.getIdVenda(), e.getMessage());
                }
            });
        }
        return converterParaDTO(venda);
    }

    // =========================================================================
    // 2. GESTÃO DE ESTADOS (SUSPENDER, EFETIVAR, CANCELAR)
    // =========================================================================

    @Transactional
    public Venda suspenderVenda(VendaRequestDTO dto) {
        Usuario usuario = capturarUsuarioLogado();
        CaixaDiario caixa = caixaRepository.findFirstByUsuarioAberturaAndStatus(usuario, StatusCaixa.ABERTO).orElse(null);

        Venda venda = new Venda();
        venda.setUsuario(usuario);
        venda.setCaixa(caixa);
        venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Venda Suspensa");
        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusNfce(StatusFiscal.EM_ESPERA);

        if (dto.clienteId() != null) {
            clienteRepository.findById(dto.clienteId()).ifPresent(venda::setCliente);
        }

        if (dto.pagamentos() != null && !dto.pagamentos().isEmpty()) {
            venda.setFormaDePagamento(dto.pagamentos().get(0).formaPagamento());
        } else {
            venda.setFormaDePagamento(FormaDePagamento.DINHEIRO);
        }

        // CORREÇÃO CRÍTICA (Furo de Caixa): Cálculo de Desconto na Suspensão
        BigDecimal totalItens = processarItensParaOrcamento(venda, dto.itens());
        BigDecimal descontoAplicado = dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO;

        venda.setDescontoTotal(descontoAplicado);

        // Agora o Valor Total da venda subtrai o desconto e impede valores negativos
        venda.setValorTotal(totalItens.subtract(descontoAplicado).max(BigDecimal.ZERO));

        venda.setObservacao(dto.observacao());

        return vendaRepository.save(venda);
    }

    @Transactional
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);

        if (venda.getStatusNfce() == StatusFiscal.CANCELADA) {
            throw new ValidationException("Esta venda já está cancelada.");
        }

        if (venda.getStatusNfce() == StatusFiscal.EM_ESPERA) {
            String motivoFinal = motivo != null ? motivo : "Retomada no PDV";
            vendaRepository.atualizarStatusVenda(idVenda, StatusFiscal.CANCELADA, motivoFinal);
            return;
        }

        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.CANCELAMENTO_DE_VENDA);
            estoqueService.realizarAjusteManual(ajuste);
        });

        financeiroService.cancelarReceitaDeVenda(idVenda);

        venda.setStatusNfce(StatusFiscal.CANCELADA);
        venda.setMotivoDoCancelamento(motivo);
        vendaRepository.save(venda);

        auditoriaService.registrar("CANCELAMENTO_VENDA", "Venda #" + idVenda + " cancelada. Motivo: " + motivo);
    }

    @Transactional
    public Venda efetivarVenda(Long idVendaPrevia) {
        Venda venda = buscarVendaComItens(idVendaPrevia);
        if (venda.getStatusNfce() != StatusFiscal.ORCAMENTO && venda.getStatusNfce() != StatusFiscal.EM_ESPERA) {
            throw new ValidationException("Apenas orçamentos ou vendas suspensas podem ser efetivadas.");
        }

        Usuario operador = capturarUsuarioLogado();
        CaixaDiario caixa = validarCaixaAberto(operador);

        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusNfce(StatusFiscal.PENDENTE);

        if (venda.getFormaDePagamento() == FormaDePagamento.CREDIARIO) {
            validarCreditoDoCliente(venda.getClienteDocumento(), venda.getValorTotal());
        }

        venda.getItens().forEach(item -> estoqueService.registrarSaidaVenda(item.getProduto(), item.getQuantidade().intValue()));

        financeiroService.lancarReceitaDeVenda(
                venda.getIdVenda(),
                venda.getValorTotal(),
                venda.getFormaDePagamento().name(),
                venda.getQuantidadeParcelas(), // Método deve existir na Venda
                buscarIdClientePorDocumento(venda.getClienteDocumento())
        );

        atualizarSaldoCaixaUnico(caixa, venda.getValorTotal(), venda.getFormaDePagamento());
        caixaRepository.save(caixa);

        // Emissão Assíncrona na Efetivação
        CompletableFuture.runAsync(() -> {
            try {
                nfceService.emitirNfce(venda);
            } catch (Exception e) {
                log.error("Erro NFCe assíncrona na venda retomada {}: {}", venda.getIdVenda(), e.getMessage());
            }
        });

        return vendaRepository.save(venda);
    }

    // =========================================================================
    // 3. CONSULTAS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<VendaResponseDTO> listarVendasSuspensas() {
        return vendaRepository.findByStatusNfce(StatusFiscal.EM_ESPERA).stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<VendaResponseDTO> listarVendas(LocalDate inicio, LocalDate fim, Pageable pageable) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDateTime.now().minusDays(30);
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();
        return vendaRepository.findByDataVendaBetween(dataInicio, dataFim, pageable).map(this::converterParaDTO);
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        Venda venda = vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
        Hibernate.initialize(venda.getPagamentos()); // Carrega pagamentos lazy
        return venda;
    }

    // =========================================================================
    // 4. ATUALIZAÇÃO FINANCEIRA
    // =========================================================================

    private void atualizarFinanceiro(CaixaDiario caixa, Venda venda, List<PagamentoVenda> pagamentos) {
        if (pagamentos == null || pagamentos.isEmpty()) {
            atualizarSaldoCaixaUnico(caixa, venda.getValorTotal(), venda.getFormaDePagamento());
            return;
        }
        for (PagamentoVenda pag : pagamentos) {
            atualizarSaldoCaixaUnico(caixa, pag.getValor(), pag.getFormaPagamento());
        }
        caixaRepository.save(caixa);
    }

    private void atualizarSaldoCaixaUnico(CaixaDiario caixa, BigDecimal valor, FormaDePagamento forma) {
        if (forma == FormaDePagamento.DINHEIRO) {
            caixa.setTotalVendasDinheiro(nvl(caixa.getTotalVendasDinheiro()).add(valor));
        } else if (forma == FormaDePagamento.PIX) {
            caixa.setTotalVendasPix(nvl(caixa.getTotalVendasPix()).add(valor));
        } else if (forma == FormaDePagamento.CREDITO || forma == FormaDePagamento.DEBITO) {
            caixa.setTotalVendasCartao(nvl(caixa.getTotalVendasCartao()).add(valor));
        }
    }

    // =========================================================================
    // 5. AUXILIARES
    // =========================================================================

    private VendaResponseDTO converterParaDTO(Venda venda) {
        List<ItemVendaResponseDTO> itensDto = venda.getItens() != null
                ? venda.getItens().stream().map(ItemVendaResponseDTO::new).collect(Collectors.toList())
                : new ArrayList<>();

        List<PagamentoResponseDTO> pagamentosDto = new ArrayList<>();
        if (venda.getPagamentos() != null) {
            pagamentosDto = venda.getPagamentos().stream()
                    .map(p -> new PagamentoResponseDTO(
                            p.getFormaPagamento(),
                            p.getValor(),
                            p.getParcelas()
                    ))
                    .collect(Collectors.toList());
        }

        return new VendaResponseDTO(
                venda.getIdVenda(),
                venda.getDataVenda(),
                venda.getValorTotal(),
                venda.getDescontoTotal(),
                venda.getClienteNome(),
                venda.getFormaDePagamento(),
                itensDto,
                pagamentosDto,
                venda.getValorIbs(),
                venda.getValorCbs(),
                venda.getValorIs(),
                venda.getValorLiquido(),
                venda.getStatusNfce(),
                venda.getChaveAcessoNfce()
        );
    }

    private void processarSaidaEstoqueComAuditoria(Produto produto, int qtdVenda) {
        if (produto.getQuantidadeEmEstoque() < qtdVenda) {
            String msg = String.format("Venda sem estoque: %s. Qtd: %d, Anterior: %d",
                    produto.getDescricao(), qtdVenda, produto.getQuantidadeEmEstoque());
            auditoriaService.registrar("ESTOQUE_NEGATIVO", msg);
        }
        estoqueService.registrarSaidaVenda(produto, qtdVenda);
    }

    private BigDecimal processarItensParaOrcamento(Venda venda, List<ItemVendaDTO> dtos) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemVendaDTO dto : dtos) {
            Produto p = produtoRepository.findById(dto.produtoId()).orElseThrow();
            ItemVenda i = new ItemVenda();
            i.setVenda(venda); i.setProduto(p); i.setQuantidade(dto.quantidade()); i.setPrecoUnitario(dto.precoUnitario());
            i.setDesconto(dto.desconto() != null ? dto.desconto() : BigDecimal.ZERO);

            if (venda.getItens() == null) venda.setItens(new ArrayList<>());
            venda.getItens().add(i);
            total = total.add(i.getPrecoUnitario().multiply(i.getQuantidade()).subtract(i.getDesconto()));
        }
        return total;
    }

    private RegraTributaria buscarRegraVigente() {
        return regraTributariaRepository.findRegraVigente(LocalDate.now())
                .orElse(new RegraTributaria(LocalDate.now().getYear(), LocalDate.now(), LocalDate.now(), "0.00", "0.00", "1.0"));
    }

    private void validarLimitesDeDesconto(Usuario usuario, BigDecimal totalVenda, BigDecimal descontoAplicado) {
        if (descontoAplicado.compareTo(BigDecimal.ZERO) <= 0) return;
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();
        ConfiguracaoLoja.DadosFinanceiro dadosFin = config.getFinanceiro();
        if (dadosFin == null) {
            dadosFin = new ConfiguracaoLoja.DadosFinanceiro();
            dadosFin.setDescCaixa(new BigDecimal("5.00"));
            dadosFin.setDescGerente(new BigDecimal("20.00"));
        }
        BigDecimal bruto = totalVenda.add(descontoAplicado);
        if (bruto.compareTo(BigDecimal.ZERO) == 0) return;
        BigDecimal percentual = descontoAplicado.divide(bruto, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        BigDecimal limite = (usuario.getPerfilDoUsuario() == PerfilDoUsuario.ROLE_ADMIN)
                ? nvl(dadosFin.getDescGerente(), new BigDecimal("100.00"))
                : nvl(dadosFin.getDescCaixa(), new BigDecimal("5.00"));
        if (percentual.compareTo(limite) > 0) {
            throw new ValidationException("Desconto de " + percentual.setScale(2, RoundingMode.HALF_UP) +
                    "% excede o limite permitido de " + limite + "%");
        }
    }

    private void aplicarDadosFiscais(Venda venda, List<ItemVenda> itens) {
        try {
            ResumoFiscalCarrinhoDTO fiscal = calculadoraFiscalService.calcularTotaisCarrinho(itens);
            venda.setValorIbs(fiscal.totalIbs());
            venda.setValorCbs(fiscal.totalCbs());
            venda.setValorIs(fiscal.totalIs());
            venda.setValorLiquido(fiscal.totalLiquido().subtract(nvl(venda.getDescontoTotal())));
        } catch (Exception e) {
            venda.setValorIbs(BigDecimal.ZERO);
            venda.setValorCbs(BigDecimal.ZERO);
        }
    }

    private Usuario capturarUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) return null;
        String login = auth.getName();
        return usuarioRepository.findByMatriculaOrEmail(login, login).orElse(null);
    }

    private CaixaDiario validarCaixaAberto(Usuario usuario) {
        return caixaRepository.findFirstByUsuarioAberturaAndStatus(usuario, StatusCaixa.ABERTO)
                .orElseThrow(() -> new ValidationException("O seu caixa está FECHADO. Abra o caixa para operar."));
    }

    private BigDecimal nvl(BigDecimal val) { return val == null ? BigDecimal.ZERO : val; }
    private BigDecimal nvl(BigDecimal val, BigDecimal padrao) { return val == null ? padrao : val; }

    private Long buscarIdClientePorDocumento(String doc) {
        if (doc == null) return null;
        return clienteRepository.findByDocumento(doc.replaceAll("\\D", "")).map(Cliente::getId).orElse(null);
    }

    private void validarCreditoDoCliente(String documento, BigDecimal valor) {
        if (documento == null) throw new ValidationException("Documento obrigatório para Crediário.");
        Cliente cliente = clienteRepository.findByDocumento(documento.replaceAll("\\D", ""))
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não cadastrado."));
        BigDecimal divida = nvl(contaReceberRepository.somarDividaTotalPorDocumento(cliente.getDocumento()));
        if (divida.add(valor).compareTo(cliente.getLimiteCredito()) > 0) throw new ValidationException("Limite de crédito excedido!");
    }
}
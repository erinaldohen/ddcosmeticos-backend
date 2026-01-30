package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

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
    @CacheEvict(value = "dashboard", allEntries = true)
    public VendaResponseDTO realizarVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();
        if (usuarioLogado == null) throw new ValidationException("Sessão inválida.");

        CaixaDiario caixa = validarCaixaAberto(usuarioLogado);

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setDataVenda(LocalDateTime.now());
        venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Consumidor Final");
        venda.setClienteDocumento(dto.clienteDocumento());

        // Define forma principal
        if (dto.pagamentos() != null && !dto.pagamentos().isEmpty()) {
            venda.setFormaDePagamento(dto.pagamentos().get(0).formaPagamento());
        } else {
            venda.setFormaDePagamento(dto.formaDePagamento() != null ? dto.formaDePagamento() : FormaDePagamento.DINHEIRO);
        }

        venda.setQuantidadeParcelas(dto.quantidadeParcelas() != null ? dto.quantidadeParcelas() : 1);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        RegraTributaria regra = buscarRegraVigente();

        // Processamento dos Itens
        List<ItemVenda> itens = dto.itens().stream().map(itemDto -> {
            Produto produto = produtoRepository.findById(itemDto.produtoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado ID: " + itemDto.produtoId()));

            processarSaidaEstoqueComAuditoria(produto, itemDto.quantidade().intValue());

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(itemDto.precoUnitario());
            item.setVenda(venda);
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);
            item.setAliquotaIbsAplicada(regra.getAliquotaIbs());
            item.setAliquotaCbsAplicada(regra.getAliquotaCbs());

            return item;
        }).collect(Collectors.toList());

        venda.setItens(itens);
        venda.setValorTotal(calcularTotalVenda(itens, venda.getDescontoTotal()));
        venda.setStatusNfce(StatusFiscal.PENDENTE);

        validarLimitesDeDesconto(usuarioLogado, venda.getValorTotal(), venda.getDescontoTotal());
        aplicarDadosFiscais(venda, itens);

        vendaRepository.save(venda);

        // Atualiza caixa
        atualizarFinanceiro(caixa, venda, dto.pagamentos());

        // Emissão NFC-e
        if (!Boolean.TRUE.equals(dto.ehOrcamento())) {
            try {
                log.info("📡 Tentando emitir NFC-e para Venda #{}", venda.getIdVenda());
                nfceService.emitirNfce(venda, false);
            } catch (Exception e) {
                log.error("⚠️ Venda salva, mas falha na emissão automática: {}", e.getMessage());
            }
        }

        return converterParaDTO(venda);
    }

    // =========================================================================
    // 2. GESTÃO DE ESTADOS (SUSPENDER, EFETIVAR, CANCELAR)
    // =========================================================================

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
                venda.getQuantidadeParcelas(),
                buscarIdClientePorDocumento(venda.getClienteDocumento())
        );

        atualizarSaldoCaixaUnico(caixa, venda.getValorTotal(), venda.getFormaDePagamento());
        caixaRepository.save(caixa);

        try {
            nfceService.emitirNfce(venda, false);
        } catch (Exception e) {
            log.error("Erro NFCe venda {}: {}", venda.getIdVenda(), e.getMessage());
        }
        return vendaRepository.save(venda);
    }

    @Transactional
    public Venda suspenderVenda(VendaRequestDTO dto) {
        Venda venda = new Venda();
        venda.setUsuario(capturarUsuarioLogado());
        venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Venda Suspensa " + LocalTime.now().toString().substring(0,5));
        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusNfce(StatusFiscal.EM_ESPERA);

        if (dto.pagamentos() != null && !dto.pagamentos().isEmpty()) {
            venda.setFormaDePagamento(dto.pagamentos().get(0).formaPagamento());
        } else {
            venda.setFormaDePagamento(dto.formaDePagamento() != null ? dto.formaDePagamento() : FormaDePagamento.DINHEIRO);
        }

        BigDecimal totalItens = processarItensParaOrcamento(venda, dto.itens());
        venda.setValorTotal(totalItens);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        return vendaRepository.save(venda);
    }

    @Transactional
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);
        if (venda.getStatusNfce() == StatusFiscal.CANCELADA) throw new ValidationException("Venda já cancelada.");

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

    // =========================================================================
    // 3. CONSULTAS (LISTAGEM) - Onde estava o erro
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
        return vendaRepository.findByIdComItens(id).orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
    }

    // =========================================================================
    // 4. ATUALIZAÇÃO FINANCEIRA
    // =========================================================================

    private void atualizarFinanceiro(CaixaDiario caixa, Venda venda, List<PagamentoRequestDTO> pagamentos) {
        if (pagamentos == null || pagamentos.isEmpty()) {
            atualizarSaldoCaixaUnico(caixa, venda.getValorTotal(), venda.getFormaDePagamento());
            return;
        }

        for (PagamentoRequestDTO pag : pagamentos) {
            atualizarSaldoCaixaUnico(caixa, pag.valor(), pag.formaPagamento());
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
    // 5. AUXILIARES E CONVERSORES
    // =========================================================================

    private void processarSaidaEstoqueComAuditoria(Produto produto, int qtdVenda) {
        if (produto.getQuantidadeEmEstoque() < qtdVenda) {
            String msg = String.format("Venda sem estoque: %s. Qtd: %d, Anterior: %d",
                    produto.getDescricao(), qtdVenda, produto.getQuantidadeEmEstoque());
            auditoriaService.registrar("ESTOQUE_NEGATIVO", msg);
        }
        estoqueService.registrarSaidaVenda(produto, qtdVenda);
    }

    private VendaResponseDTO converterParaDTO(Venda venda) {
        // 1. Converte Itens
        List<ItemVendaResponseDTO> itensDto = venda.getItens() != null
                ? venda.getItens().stream().map(ItemVendaResponseDTO::new).collect(Collectors.toList())
                : new ArrayList<>();

        // 2. Converte Pagamentos (CORREÇÃO DO ERRO 500)
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
                pagamentosDto, // <--- Passando a lista segura
                venda.getValorIbs(),
                venda.getValorCbs(),
                venda.getValorIs(),
                venda.getValorLiquido(),
                venda.getStatusNfce(),
                venda.getChaveAcessoNfce()
        );
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

    private BigDecimal processarItensParaOrcamento(Venda venda, List<ItemVendaDTO> dtos) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemVendaDTO dto : dtos) {
            Produto p = produtoRepository.findById(dto.produtoId()).orElseThrow();
            ItemVenda i = new ItemVenda();
            i.setVenda(venda); i.setProduto(p); i.setQuantidade(dto.quantidade()); i.setPrecoUnitario(dto.precoUnitario());
            if (venda.getItens() == null) venda.setItens(new ArrayList<>());
            venda.getItens().add(i);
            total = total.add(i.getPrecoUnitario().multiply(i.getQuantidade()));
        }
        return total;
    }

    private RegraTributaria buscarRegraVigente() {
        return regraTributariaRepository.findRegraVigente(LocalDate.now())
                .orElse(new RegraTributaria(LocalDate.now().getYear(), LocalDate.now(), LocalDate.now(), "0.00", "0.00", "1.0"));
    }

    private BigDecimal calcularTotalVenda(List<ItemVenda> itens, BigDecimal desconto) {
        BigDecimal bruto = itens.stream().map(i -> i.getPrecoUnitario().multiply(i.getQuantidade()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return bruto.subtract(nvl(desconto));
    }

    private void aplicarDadosFiscais(Venda venda, List<ItemVenda> itens) {
        try {
            ResumoFiscalCarrinhoDTO fiscal = calculadoraFiscalService.calcularTotaisCarrinho(itens);
            venda.setValorIbs(fiscal.totalIbs());
            venda.setValorCbs(fiscal.totalCbs());
            venda.setValorIs(fiscal.totalIs());
            venda.setValorLiquido(fiscal.totalLiquido().subtract(nvl(venda.getDescontoTotal())));
        } catch (Exception e) {
            log.warn("Erro ao calcular impostos: {}", e.getMessage());
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

    private BigDecimal nvl(BigDecimal val) { return val == null ? BigDecimal.ZERO : val; }
    private BigDecimal nvl(BigDecimal val, BigDecimal padrao) { return val == null ? padrao : val; }
}
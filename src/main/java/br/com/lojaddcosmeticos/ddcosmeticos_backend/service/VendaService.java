package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor // Otimização: Injeção de dependência pelo construtor (Mais rápido e seguro que @Autowired em campos)
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final ConfiguracaoLojaService configuracaoLojaService;
    private final RegraTributariaRepository regraTributariaRepository;
    private final CaixaDiarioRepository caixaRepository;
    private final EstoqueService estoqueService;
    private final FinanceiroService financeiroService;
    private final NfceService nfceService;
    private final CalculadoraFiscalService calculadoraFiscalService;
    private final AuditoriaService auditoriaService;
    private final FiscalComplianceService fiscalComplianceService;

    // =========================================================================
    // 1. PROCESSAMENTO DE VENDAS (FINALIZAÇÃO)
    // =========================================================================

    @Transactional(rollbackFor = Exception.class) // Proteção ACID: Reverte a transação caso ocorra QUALQUER erro
    public VendaResponseDTO realizarVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();
        if (usuarioLogado == null) throw new ValidationException("Sessão inválida ou expirada.");

        // TRAVA DE CAIXA
        CaixaDiario caixa = validarCaixaAberto(usuarioLogado);

        if (dto.pagamentos() == null || dto.pagamentos().isEmpty()) {
            throw new ValidationException("É necessário informar ao menos uma forma de pagamento.");
        }

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setDataVenda(LocalDateTime.now());
        venda.setCaixa(caixa);

        // MAPEAMENTO DO CLIENTE
        if (dto.clienteId() != null) {
            Cliente cliente = clienteRepository.findById(dto.clienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado ID: " + dto.clienteId()));
            venda.setCliente(cliente);
            venda.setClienteNome(cliente.getNome());
            venda.setClienteDocumento(cliente.getDocumento());
            venda.setClienteTelefone(cliente.getTelefone());
        } else {
            venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Consumidor Final");
            venda.setClienteDocumento(dto.clienteDocumento());
            venda.setClienteTelefone(dto.clienteTelefone());
        }

        venda.setDescontoTotal(nvl(dto.descontoTotal()));
        RegraTributaria regra = buscarRegraVigente();

        // OTIMIZAÇÃO DE BANCO DE DADOS: Evitar N+1 Queries ao buscar produtos
        List<Long> idsProdutos = dto.itens().stream().map(ItemVendaDTO::produtoId).collect(Collectors.toList());
        Map<Long, Produto> mapProdutos = produtoRepository.findAllById(idsProdutos).stream()
                .collect(Collectors.toMap(Produto::getId, p -> p));

        // Processamento dos Itens
        List<ItemVenda> itens = dto.itens().stream().map(itemDto -> {
            Produto produto = mapProdutos.get(itemDto.produtoId());
            if (produto == null) throw new ResourceNotFoundException("Produto não encontrado ID: " + itemDto.produtoId());

            processarSaidaEstoqueComAuditoria(produto, itemDto.quantidade().intValue());

            ItemVenda item = new ItemVenda();
            item.setVenda(venda);
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(nvl(itemDto.precoUnitario()));
            item.setDesconto(nvl(itemDto.desconto()));

            item.setCustoUnitarioHistorico(nvl(produto.getPrecoCusto()));
            item.setAliquotaIbsAplicada(regra.getAliquotaIbs());
            item.setAliquotaCbsAplicada(regra.getAliquotaCbs());

            fiscalComplianceService.aplicarComplianceNoItemVenda(item);
            return item;
        }).collect(Collectors.toList());

        venda.setItens(itens);

        // Cálculos Financeiros
        BigDecimal subtotalItens = itens.stream()
                .map(i -> nvl(i.getPrecoUnitario()).multiply(new BigDecimal(i.getQuantidade().toString())).subtract(nvl(i.getDesconto())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorFinal = subtotalItens.subtract(nvl(venda.getDescontoTotal())).max(BigDecimal.ZERO);
        venda.setValorTotal(valorFinal);

        BigDecimal totalPago = dto.pagamentos().stream().map(p -> nvl(p.valor())).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPago.compareTo(valorFinal) < 0 && totalPago.subtract(valorFinal).abs().compareTo(new BigDecimal("0.05")) > 0) {
            throw new ValidationException("O valor pago (R$ " + totalPago + ") é insuficiente.");
        }

        venda.setTroco(totalPago.subtract(valorFinal).max(BigDecimal.ZERO));

        List<PagamentoVenda> pagamentos = dto.pagamentos().stream().map(pgDto -> {
            PagamentoVenda pg = new PagamentoVenda();
            pg.setVenda(venda);
            pg.setFormaPagamento(pgDto.formaPagamento());
            pg.setValor(nvl(pgDto.valor()));
            pg.setParcelas(pgDto.parcelas() != null ? pgDto.parcelas() : 1);
            return pg;
        }).collect(Collectors.toList());

        venda.setPagamentos(pagamentos);
        if (!pagamentos.isEmpty()) venda.setFormaDePagamento(pagamentos.get(0).getFormaPagamento());

        venda.setStatusNfce(StatusFiscal.PENDENTE);

        BigDecimal totalDescontosConcedidos = venda.getDescontoTotal().add(itens.stream().map(i -> nvl(i.getDesconto())).reduce(BigDecimal.ZERO, BigDecimal::add));

        validarLimitesDeDesconto(usuarioLogado, subtotalItens, totalDescontosConcedidos);
        aplicarDadosFiscais(venda, itens);

        // GRAVAÇÃO DA VENDA NO BANCO
        vendaRepository.save(venda);
        atualizarFinanceiro(caixa, venda, pagamentos);

        // =====================================================================
        // TRILHA DE AUDITORIA (ANTI-FRAUDE)
        // =====================================================================
        if (dto.logAuditoria() != null && !dto.logAuditoria().isEmpty()) {
            for (VendaRequestDTO.LogAuditoriaPDVDTO logPDV : dto.logAuditoria()) {
                String mensagemFormatada = String.format("[Ação: %s] %s | Marcador: %s | Venda ID: %d",
                        logPDV.acao(), logPDV.detalhes(), logPDV.hora(), venda.getIdVenda());
                auditoriaService.registrar("ALERTA_PDV", mensagemFormatada);
            }
        }
        // =====================================================================

        // PROCESSAMENTO ASSÍNCRONO DA NFC-e
        if (!Boolean.TRUE.equals(dto.ehOrcamento())) {
            CompletableFuture.runAsync(() -> {
                try { nfceService.emitirNfce(venda); }
                catch (Exception e) { log.error("Erro na emissão NFC-e venda {}: {}", venda.getIdVenda(), e.getMessage()); }
            });
        }
        return converterParaDTO(venda);
    }

    // =========================================================================
    // 2. GESTÃO DE ESTADOS (SUSPENDER, CANCELAR, EFETIVAR)
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public Venda suspenderVenda(VendaRequestDTO dto) {
        Usuario usuario = capturarUsuarioLogado();
        CaixaDiario caixa = validarCaixaAberto(usuario);

        Venda venda = new Venda();
        venda.setUsuario(usuario);
        venda.setCaixa(caixa);
        venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Venda Suspensa");
        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusNfce(StatusFiscal.EM_ESPERA);

        if (dto.clienteId() != null) {
            clienteRepository.findById(dto.clienteId()).ifPresent(venda::setCliente);
        } else {
            venda.setClienteDocumento(dto.clienteDocumento());
            venda.setClienteTelefone(dto.clienteTelefone());
        }

        if (dto.pagamentos() != null && !dto.pagamentos().isEmpty()) {
            venda.setFormaDePagamento(dto.pagamentos().get(0).formaPagamento());
        } else {
            venda.setFormaDePagamento(FormaDePagamento.DINHEIRO);
        }

        BigDecimal totalItens = processarItensParaOrcamento(venda, dto.itens());
        BigDecimal descontoAplicado = nvl(dto.descontoTotal());
        venda.setDescontoTotal(descontoAplicado);
        venda.setValorTotal(totalItens.subtract(descontoAplicado).max(BigDecimal.ZERO));
        venda.setObservacao(dto.observacao());

        return vendaRepository.save(venda);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);
        if (venda.getStatusNfce() == StatusFiscal.CANCELADA) throw new ValidationException("Esta venda já está cancelada.");

        if (venda.getStatusNfce() == StatusFiscal.EM_ESPERA) {
            vendaRepository.atualizarStatusVenda(idVenda, StatusFiscal.CANCELADA, motivo != null ? motivo : "Retomada no PDV");
            return;
        }

        // DEVOLVE ITENS AO ESTOQUE
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

    @Transactional(rollbackFor = Exception.class)
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

        financeiroService.lancarReceitaDeVenda(venda.getIdVenda(), venda.getValorTotal(), venda.getFormaDePagamento().name(), venda.getQuantidadeParcelas(), buscarIdClientePorDocumento(venda.getClienteDocumento()));

        atualizarFinanceiro(caixa, venda, venda.getPagamentos());

        CompletableFuture.runAsync(() -> {
            try { nfceService.emitirNfce(venda); }
            catch (Exception e) { log.error("Erro NFCe venda retomada {}: {}", venda.getIdVenda(), e.getMessage()); }
        });

        return vendaRepository.save(venda);
    }

    // =========================================================================
    // 3. CONSULTAS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<VendaResponseDTO> listarVendasSuspensas() {
        return vendaRepository.findByStatusNfce(StatusFiscal.EM_ESPERA).stream().map(this::converterParaDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<VendaResponseDTO> listarVendas(LocalDate inicio, LocalDate fim, Pageable pageable) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDateTime.now().minusDays(30);
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();
        return vendaRepository.findByDataVendaBetween(dataInicio, dataFim, pageable).map(this::converterParaDTO);
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        Venda venda = vendaRepository.findByIdComItens(id).orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
        Hibernate.initialize(venda.getPagamentos());
        return venda;
    }

    // =========================================================================
    // 4. ATUALIZAÇÃO FINANCEIRA DE CAIXA
    // =========================================================================

    private void atualizarFinanceiro(CaixaDiario caixa, Venda venda, List<PagamentoVenda> pagamentos) {
        if (pagamentos == null || pagamentos.isEmpty()) return;
        BigDecimal trocoRestante = nvl(venda.getTroco());

        for (PagamentoVenda pg : pagamentos) {
            BigDecimal valorReal = nvl(pg.getValor());

            // Lógica de Abatimento de Troco (Somente deduzido do dinheiro em espécie)
            if (pg.getFormaPagamento() == FormaDePagamento.DINHEIRO && trocoRestante.compareTo(BigDecimal.ZERO) > 0) {
                if (valorReal.compareTo(trocoRestante) >= 0) {
                    valorReal = valorReal.subtract(trocoRestante);
                    trocoRestante = BigDecimal.ZERO;
                } else {
                    trocoRestante = trocoRestante.subtract(valorReal);
                    valorReal = BigDecimal.ZERO;
                }
            }

            // Só contabiliza no caixa se ainda houver valor após deduzir o troco
            if (valorReal.compareTo(BigDecimal.ZERO) > 0) {
                switch (pg.getFormaPagamento()) {
                    case DINHEIRO -> {
                        caixa.setTotalVendasDinheiro(nvl(caixa.getTotalVendasDinheiro()).add(valorReal));
                        caixa.setSaldoAtual(nvl(caixa.getSaldoAtual()).add(valorReal));
                    }
                    case PIX -> caixa.setTotalVendasPix(nvl(caixa.getTotalVendasPix()).add(valorReal));
                    case CREDITO, CARTAO_CREDITO -> {
                        caixa.setTotalVendasCredito(nvl(caixa.getTotalVendasCredito()).add(valorReal));
                        caixa.setTotalVendasCartao(nvl(caixa.getTotalVendasCartao()).add(valorReal));
                    }
                    case DEBITO, CARTAO_DEBITO -> {
                        caixa.setTotalVendasDebito(nvl(caixa.getTotalVendasDebito()).add(valorReal));
                        caixa.setTotalVendasCartao(nvl(caixa.getTotalVendasCartao()).add(valorReal));
                    }
                    default -> log.warn("Forma não mapeada no caixa: {}", pg.getFormaPagamento());
                }
            }
        }
        caixaRepository.save(caixa);
    }

    // =========================================================================
    // 5. AUXILIARES E VALIDAÇÕES (LÓGICA BLINDADA)
    // =========================================================================

    private BigDecimal nvl(BigDecimal val) {
        return val == null ? BigDecimal.ZERO : val;
    }

    private BigDecimal nvl(BigDecimal val, BigDecimal padrao) {
        return val == null ? padrao : val;
    }

    private VendaResponseDTO converterParaDTO(Venda venda) {
        List<ItemVendaResponseDTO> itensDto = venda.getItens() != null
                ? venda.getItens().stream().map(i -> {
            String nome = (i.getProduto() != null && i.getProduto().getDescricao() != null) ? i.getProduto().getDescricao() : "Produto Desconhecido";
            String ean = (i.getProduto() != null) ? i.getProduto().getCodigoBarras() : "Sem EAN";
            return new ItemVendaResponseDTO(i.getProduto() != null ? i.getProduto().getId() : null, nome, ean, i.getQuantidade(), i.getPrecoUnitario(), i.getDesconto());
        }).collect(Collectors.toList()) : new ArrayList<>();

        List<PagamentoResponseDTO> pagamentosDto = venda.getPagamentos() != null
                ? venda.getPagamentos().stream().map(p -> new PagamentoResponseDTO(p.getFormaPagamento(), p.getValor(), p.getParcelas())).collect(Collectors.toList()) : new ArrayList<>();

        return new VendaResponseDTO(
                venda.getIdVenda(), venda.getDataVenda(), venda.getValorTotal(), venda.getDescontoTotal(),
                venda.getClienteNome(), venda.getFormaDePagamento(), itensDto, pagamentosDto,
                venda.getValorIbs(), venda.getValorCbs(), venda.getValorIs(), venda.getValorLiquido(),
                venda.getStatusNfce(), venda.getChaveAcessoNfce(), venda.getObservacao()
        );
    }

    private void processarSaidaEstoqueComAuditoria(Produto produto, int qtdVenda) {
        if (produto.getQuantidadeEmEstoque() < qtdVenda) {
            auditoriaService.registrar("ESTOQUE_NEGATIVO", String.format("Venda sem estoque: %s. Qtd: %d, Anterior: %d", produto.getDescricao(), qtdVenda, produto.getQuantidadeEmEstoque()));
        }
        estoqueService.registrarSaidaVenda(produto, qtdVenda);
    }

    private BigDecimal processarItensParaOrcamento(Venda venda, List<ItemVendaDTO> dtos) {
        BigDecimal total = BigDecimal.ZERO;

        List<Long> idsProdutos = dtos.stream().map(ItemVendaDTO::produtoId).collect(Collectors.toList());
        Map<Long, Produto> mapProdutos = produtoRepository.findAllById(idsProdutos).stream()
                .collect(Collectors.toMap(Produto::getId, p -> p));

        for (ItemVendaDTO dto : dtos) {
            Produto p = mapProdutos.get(dto.produtoId());
            if (p == null) throw new ResourceNotFoundException("Produto não encontrado: " + dto.produtoId());

            ItemVenda i = new ItemVenda(); i.setVenda(venda); i.setProduto(p); i.setQuantidade(dto.quantidade()); i.setPrecoUnitario(nvl(dto.precoUnitario())); i.setDesconto(nvl(dto.desconto()));
            if (venda.getItens() == null) venda.setItens(new ArrayList<>());
            venda.getItens().add(i);
            total = total.add(i.getPrecoUnitario().multiply(new BigDecimal(String.valueOf(i.getQuantidade()))).subtract(i.getDesconto()));
        }
        return total;
    }

    private RegraTributaria buscarRegraVigente() {
        return regraTributariaRepository.findRegraVigente(LocalDate.now()).orElse(new RegraTributaria(LocalDate.now().getYear(), LocalDate.now(), LocalDate.now(), "0.00", "0.00", "1.0"));
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
                ? nvl(dadosFin.getDescGerente(), new BigDecimal("20.00"))
                : nvl(dadosFin.getDescCaixa(), new BigDecimal("5.00"));

        if (percentual.compareTo(limite) > 0) {
            throw new ValidationException("Desconto de " + percentual.setScale(2, RoundingMode.HALF_UP) +
                    "% excede o limite permitido de " + limite + "%");
        }
    }

    private void aplicarDadosFiscais(Venda venda, List<ItemVenda> itens) {
        try {
            ResumoFiscalCarrinhoDTO fiscal = calculadoraFiscalService.calcularTotaisCarrinho(itens);
            venda.setValorIbs(nvl(fiscal.totalIbs())); venda.setValorCbs(nvl(fiscal.totalCbs()));
            venda.setValorIs(nvl(fiscal.totalIs())); venda.setValorLiquido(nvl(fiscal.totalLiquido()).subtract(nvl(venda.getDescontoTotal())));
        } catch (Exception e) { venda.setValorIbs(BigDecimal.ZERO); venda.setValorCbs(BigDecimal.ZERO); }
    }

    private Usuario capturarUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) return null;
        return usuarioRepository.findByMatriculaOrEmail(auth.getName(), auth.getName()).orElse(null);
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
        Cliente cliente = clienteRepository.findByDocumento(documento.replaceAll("\\D", "")).orElseThrow(() -> new ResourceNotFoundException("Cliente não cadastrado."));
        BigDecimal divida = nvl(contaReceberRepository.somarDividaTotalPorDocumento(cliente.getDocumento()));
        if (divida.add(valor).compareTo(cliente.getLimiteCredito()) > 0) throw new ValidationException("Limite de crédito excedido!");
    }
}
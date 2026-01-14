package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import org.springframework.cache.annotation.CacheEvict;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    @Autowired private ConfiguracaoLojaRepository configuracaoLojaRepository;
    @Autowired private RegraTributariaRepository regraTributariaRepository;
    @Autowired private EstoqueService estoqueService;
    @Autowired private FinanceiroService financeiroService;
    @Autowired private NfceService nfceService;
    @Autowired private CalculadoraFiscalService calculadoraFiscalService;

    // [NOVO] Injeção necessária para registrar o alerta sem travar a venda
    @Autowired private AuditoriaService auditoriaService;

    @Transactional
    @CacheEvict(value = "dashboard", allEntries = true)
    public VendaResponseDTO realizarVenda(VendaRequestDTO dto) {
        Venda venda = new Venda();
        venda.setDataVenda(LocalDateTime.now());
        venda.setClienteNome(dto.clienteNome());
        venda.setClienteDocumento(dto.clienteDocumento());
        venda.setFormaDePagamento(dto.formaDePagamento());
        venda.setQuantidadeParcelas(dto.quantidadeParcelas() != null ? dto.quantidadeParcelas() : 1);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        LocalDate hoje = LocalDate.now();
        RegraTributaria regra = regraTributariaRepository.findRegraVigente(hoje)
                .orElse(new RegraTributaria(hoje.getYear(), hoje, hoje, "0.00", "0.00", "1.0"));

        List<ItemVenda> itens = dto.itens().stream().map(itemDto -> {
            Produto produto = produtoRepository.findById(itemDto.produtoId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + itemDto.produtoId()));

            // --- ALTERAÇÃO SOLICITADA: PERMITIR VENDA COM ESTOQUE NEGATIVO + ALERTA ---
            int qtdVenda = itemDto.quantidade().intValue();

            if (produto.getQuantidadeEmEstoque() < qtdVenda) {
                // Não lançamos mais throw new ValidationException(...)

                // Em vez disso, registramos um Alerta de Auditoria para o Gerente
                String msgAuditoria = String.format(
                        "ALERTA: Venda de item sem estoque sistêmico. Produto: %s (Cód: %s). Estoque Atual: %d, Vendido: %d. O estoque ficará negativo.",
                        produto.getDescricao(),
                        produto.getCodigoBarras(),
                        produto.getQuantidadeEmEstoque(),
                        qtdVenda
                );

                // Grava no banco de dados na tabela 'auditoria'
                auditoriaService.registrar("ESTOQUE_NEGATIVO", msgAuditoria);
                log.warn(msgAuditoria);
            }

            // O EstoqueService deve estar preparado para aceitar números negativos (Matematicamente o Java aceita)
            estoqueService.registrarSaidaVenda(produto, qtdVenda);

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(itemDto.precoUnitario());
            item.setVenda(venda);
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);

            item.setAliquotaIbsAplicada(regra.getAliquotaIbs());
            item.setAliquotaCbsAplicada(regra.getAliquotaCbs());

            if (produto.isImpostoSeletivo()) {
                BigDecimal valorItem = item.getPrecoUnitario().multiply(item.getQuantidade());
                item.setValorImpostoSeletivo(valorItem.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP));
            }

            return item;
        }).collect(Collectors.toList());

        venda.setItens(itens);

        BigDecimal totalBruto = itens.stream()
                .map(i -> i.getPrecoUnitario().multiply(new BigDecimal(i.getQuantidade().toString())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        venda.setValorTotal(totalBruto.subtract(venda.getDescontoTotal()));

        ResumoFiscalCarrinhoDTO fiscal = calculadoraFiscalService.calcularTotaisCarrinho(itens);
        venda.setValorIbs(fiscal.totalIbs());
        venda.setValorCbs(fiscal.totalCbs());
        venda.setValorIs(fiscal.totalIs());
        venda.setValorLiquido(fiscal.totalLiquido().subtract(venda.getDescontoTotal()));
        venda.setStatusNfce(StatusFiscal.PENDENTE);

        vendaRepository.save(venda);
        return converterParaDTO(venda);
    }

    private VendaResponseDTO converterParaDTO(Venda venda) {
        List<ItemVendaResponseDTO> itensDto = venda.getItens().stream()
                .map(ItemVendaResponseDTO::new)
                .collect(Collectors.toList());

        StatusFiscal status = venda.getStatusNfce();

        return new VendaResponseDTO(
                venda.getIdVenda(),
                venda.getDataVenda(),
                venda.getValorTotal(),
                venda.getDescontoTotal(),
                venda.getClienteNome(),
                venda.getFormaDePagamento(),
                itensDto,
                venda.getValorIbs(),
                venda.getValorCbs(),
                venda.getValorIs(),
                venda.getValorLiquido(),
                status,
                venda.getChaveAcessoNfce()
        );
    }

    @Transactional
    public Venda suspenderVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();
        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setClienteDocumento(dto.clienteDocumento());
        venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Venda Suspensa - " + LocalTime.now().toString().substring(0,5));
        venda.setDataVenda(LocalDateTime.now());
        venda.setFormaDePagamento(dto.formaDePagamento() != null ? dto.formaDePagamento() : FormaDePagamento.DINHEIRO);
        venda.setQuantidadeParcelas(dto.quantidadeParcelas() != null ? dto.quantidadeParcelas() : 1);
        venda.setStatusNfce(StatusFiscal.EM_ESPERA);

        if (dto.clienteDocumento() != null) {
            clienteRepository.findByDocumento(dto.clienteDocumento().replaceAll("\\D", "")).ifPresent(c -> venda.setClienteDocumento(c.getDocumento()));
        }

        BigDecimal totalItens = processarItensDaVenda(venda, dto);
        venda.setValorTotal(totalItens);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);
        return vendaRepository.save(venda);
    }

    @Transactional(readOnly = true)
    public List<VendaResponseDTO> listarVendasSuspensas() {
        return vendaRepository.findByStatusNfceOrderByDataVendaDesc(StatusFiscal.EM_ESPERA).stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public Venda efetivarVenda(Long idVendaPrevia) {
        Venda venda = buscarVendaComItens(idVendaPrevia);
        if (venda.getStatusNfce() != StatusFiscal.ORCAMENTO && venda.getStatusNfce() != StatusFiscal.EM_ESPERA) {
            throw new ValidationException("Apenas orçamentos ou vendas suspensas podem ser efetivadas.");
        }
        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusNfce(StatusFiscal.PENDENTE);

        if (venda.getFormaDePagamento() == FormaDePagamento.CREDIARIO) {
            BigDecimal valorFinal = venda.getValorTotal().subtract(venda.getDescontoTotal());
            String doc = venda.getClienteDocumento() != null ? venda.getClienteDocumento().replaceAll("\\D", "") : null;
            validarCreditoDoCliente(doc, valorFinal);
        }

        venda.getItens().forEach(item -> {
            estoqueService.registrarSaidaVenda(item.getProduto(), item.getQuantidade().intValue());
        });

        Long clienteId = null;
        if (venda.getClienteDocumento() != null) {
            clienteId = clienteRepository.findByDocumento(venda.getClienteDocumento().replaceAll("\\D", ""))
                    .map(Cliente::getId)
                    .orElse(null);
        }

        financeiroService.lancarReceitaDeVenda(
                venda.getIdVenda(),
                venda.getValorTotal().subtract(venda.getDescontoTotal()),
                venda.getFormaDePagamento().name(),
                venda.getQuantidadeParcelas() != null ? venda.getQuantidadeParcelas() : 1,
                clienteId
        );

        try {
            nfceService.emitirNfce(venda, false);
        } catch (Exception e) {
            log.error("Erro ao emitir NFCe na efetivação {}: {}", venda.getIdVenda(), e.getMessage());
        }
        return vendaRepository.save(venda);
    }

    @Transactional(readOnly = true)
    public Page<VendaResponseDTO> listarVendas(LocalDate inicio, LocalDate fim, Pageable pageable) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDateTime.now().minusDays(30);
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();
        return vendaRepository.findByDataVendaBetween(dataInicio, dataFim, pageable)
                .map(this::converterParaDTO);
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
    }

    @Transactional
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);
        if (venda.getStatusNfce() == StatusFiscal.CANCELADA) throw new ValidationException("Venda já cancelada.");

        if (venda.getStatusNfce() == StatusFiscal.ORCAMENTO || venda.getStatusNfce() == StatusFiscal.EM_ESPERA) {
            venda.setStatusNfce(StatusFiscal.CANCELADA);
            venda.setMotivoDoCancelamento(motivo);
            vendaRepository.save(venda);
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
    }

    private BigDecimal processarItensDaVenda(Venda venda, VendaRequestDTO dto) {
        BigDecimal totalAcumulado = BigDecimal.ZERO;
        LocalDate hoje = LocalDate.now();
        RegraTributaria regra = regraTributariaRepository.findRegraVigente(hoje)
                .orElse(new RegraTributaria(hoje.getYear(), hoje, hoje, "0.00", "0.00", "1.0"));

        for (ItemVendaDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.codigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDto.codigoBarras()));

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());
            item.setVenda(venda);
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);
            item.setAliquotaIbsAplicada(regra.getAliquotaIbs());
            item.setAliquotaCbsAplicada(regra.getAliquotaCbs());

            if (produto.isImpostoSeletivo()) {
                BigDecimal valorItem = item.getPrecoUnitario().multiply(item.getQuantidade());
                item.setValorImpostoSeletivo(valorItem.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP));
            }

            venda.adicionarItem(item);
            totalAcumulado = totalAcumulado.add(item.getTotalItem());
        }
        return totalAcumulado;
    }

    private void validarLimitesDeDesconto(Usuario usuario, BigDecimal totalVenda, BigDecimal descontoAplicado) {
        if (descontoAplicado.compareTo(BigDecimal.ZERO) <= 0) return;
        if (totalVenda.compareTo(BigDecimal.ZERO) == 0) return;

        ConfiguracaoLoja config = configuracaoLojaRepository.findById(1L).orElseGet(() -> {
            ConfiguracaoLoja nova = new ConfiguracaoLoja();
            nova.setPercentualMaximoDescontoCaixa(new BigDecimal("5.00"));
            nova.setPercentualMaximoDescontoGerente(new BigDecimal("100.00"));
            return nova;
        });

        BigDecimal percentualAplicado = descontoAplicado.divide(totalVenda, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

        BigDecimal limitePermitido = (usuario.getPerfilDoUsuario() == PerfilDoUsuario.ROLE_ADMIN || usuario.getPerfilDoUsuario() == PerfilDoUsuario.ROLE_USUARIO)
                ? config.getPercentualMaximoDescontoGerente()
                : config.getPercentualMaximoDescontoCaixa();

        if (percentualAplicado.compareTo(limitePermitido) > 0) {
            throw new ValidationException(String.format("Desconto não autorizado. Aplicado: %.2f%% | Limite: %.2f%%", percentualAplicado, limitePermitido));
        }
    }

    private void validarCreditoDoCliente(String documento, BigDecimal valorDaCompra) {
        if (documento == null || documento.isBlank()) throw new ValidationException("Documento obrigatório para Crediário.");
        Cliente cliente = clienteRepository.findByDocumento(documento).orElseThrow(() -> new ValidationException("Cliente não cadastrado."));
        if (!cliente.isAtivo()) throw new ValidationException("Cliente bloqueado.");

        if (contaReceberRepository.existeContaVencida(documento, LocalDate.now())) throw new ValidationException("Cliente com contas vencidas.");

        BigDecimal dividaAtual = contaReceberRepository.somarDividaTotalPorDocumento(documento);
        if (dividaAtual == null) dividaAtual = BigDecimal.ZERO;

        if (dividaAtual.add(valorDaCompra).compareTo(cliente.getLimiteCredito()) > 0) {
            throw new ValidationException("Limite de Crédito Excedido.");
        }
    }

    private Usuario capturarUsuarioLogado() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            if (auth.getPrincipal() instanceof Usuario) return (Usuario) auth.getPrincipal();
            if (auth.getPrincipal() instanceof UserDetails) return usuarioRepository.findByMatricula(((UserDetails) auth.getPrincipal()).getUsername()).orElse(null);
            if (auth.getPrincipal() instanceof String) return usuarioRepository.findByMatricula((String) auth.getPrincipal()).orElse(null);
        } catch (Exception e) {
            log.warn("Erro ao identificar utilizador", e);
        }
        return null;
    }
}
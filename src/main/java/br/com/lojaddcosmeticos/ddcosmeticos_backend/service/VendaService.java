package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.*;
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
    @Autowired private ConfiguracaoLojaRepository configuracaoLojaRepository;

    @Autowired private EstoqueService estoqueService;
    @Autowired private FinanceiroService financeiroService;
    @Autowired private NfceService nfceService;

    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();
        if (usuarioLogado == null) throw new ValidationException("Erro crítico: Nenhum utilizador identificado.");

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setClienteCpf(dto.clienteDocumento());
        venda.setClienteNome(dto.clienteNome());
        venda.setDataVenda(LocalDateTime.now());

        if (dto.pagamentos() != null && !dto.pagamentos().isEmpty()) {
            venda.setFormaPagamento(dto.pagamentos().get(0).formaPagamento());
        } else {
            throw new ValidationException("Informe pelo menos uma forma de pagamento.");
        }

        if (dto.clienteDocumento() != null && !dto.clienteDocumento().isBlank()) {
            String docLimpo = dto.clienteDocumento().replaceAll("\\D", "");
            clienteRepository.findByDocumento(docLimpo).ifPresent(c -> {
                venda.setCliente(c);
                if (venda.getClienteNome() == null || venda.getClienteNome().equals("Consumidor Final")) {
                    venda.setClienteNome(c.getNome());
                }
            });
        }

        boolean isOrcamento = Boolean.TRUE.equals(dto.ehOrcamento());
        venda.setStatusFiscal(isOrcamento ? StatusFiscal.ORCAMENTO : StatusFiscal.PENDENTE);

        BigDecimal totalItens = processarItensDaVenda(venda, dto);
        venda.setTotalVenda(totalItens);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        validarLimitesDeDesconto(usuarioLogado, totalItens, venda.getDescontoTotal());

        BigDecimal valorFinal = venda.getTotalVenda().subtract(venda.getDescontoTotal());
        if (valorFinal.compareTo(BigDecimal.ZERO) < 0) valorFinal = BigDecimal.ZERO;

        if (!isOrcamento) {
            boolean temCrediario = dto.pagamentos().stream().anyMatch(p -> p.formaPagamento() == FormaDePagamento.CREDIARIO);
            if (temCrediario) {
                String doc = dto.clienteDocumento() != null ? dto.clienteDocumento().replaceAll("\\D", "") : null;
                validarCreditoDoCliente(doc, valorFinal);
            }
        }

        Venda vendaSalva = vendaRepository.save(venda);

        if (!isOrcamento) {
            executarFluxosOperacionais(vendaSalva, dto);
            try {
                nfceService.emitirNfce(vendaSalva, Boolean.TRUE.equals(dto.apenasItensComNfEntrada()));
            } catch (Exception e) {
                log.error("Erro ao emitir NFC-e, mas venda foi salva", e);
            }
        }

        return vendaRepository.save(vendaSalva);
    }

    @Transactional
    public Venda suspenderVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();
        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setClienteCpf(dto.clienteDocumento());
        venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Venda Suspensa - " + LocalTime.now().toString().substring(0,5));
        venda.setDataVenda(LocalDateTime.now());

        venda.setFormaPagamento(FormaDePagamento.DINHEIRO);
        venda.setStatusFiscal(StatusFiscal.EM_ESPERA);

        if (dto.clienteDocumento() != null) {
            clienteRepository.findByDocumento(dto.clienteDocumento().replaceAll("\\D", "")).ifPresent(venda::setCliente);
        }

        BigDecimal totalItens = processarItensDaVenda(venda, dto);
        venda.setTotalVenda(totalItens);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        return vendaRepository.save(venda);
    }

    @Transactional(readOnly = true)
    public List<VendaResponseDTO> listarVendasSuspensas() {
        return vendaRepository.findByStatusFiscalOrderByDataVendaDesc(StatusFiscal.EM_ESPERA).stream()
                .map(v -> VendaResponseDTO.builder()
                        .idVenda(v.getId())
                        .dataVenda(v.getDataVenda())
                        .clienteNome(v.getClienteNome())
                        .clienteDocumento(v.getClienteCpf())
                        .valorTotal(v.getTotalVenda().subtract(v.getDescontoTotal()))
                        .totalItens(v.getItens().size())
                        .statusFiscal(v.getStatusFiscal())
                        .alertas(new ArrayList<>())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public Venda efetivarVenda(Long idVendaPrevia) {
        Venda venda = buscarVendaComItens(idVendaPrevia);
        if (venda.getStatusFiscal() != StatusFiscal.ORCAMENTO && venda.getStatusFiscal() != StatusFiscal.EM_ESPERA) {
            throw new ValidationException("Apenas orçamentos ou vendas suspensas podem ser efetivadas.");
        }
        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusFiscal(StatusFiscal.PENDENTE);

        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA);
            ajuste.setObservacao("Efetivação Venda #" + venda.getId());
            estoqueService.realizarAjusteInventario(ajuste);
        });

        Long clienteId = venda.getCliente() != null ? venda.getCliente().getId() : null;

        financeiroService.lancarReceitaDeVenda(
                venda.getId(),
                venda.getTotalVenda().subtract(venda.getDescontoTotal()),
                venda.getFormaPagamento().name(),
                venda.getQuantidadeParcelas() != null ? venda.getQuantidadeParcelas() : 1,
                clienteId
        );

        nfceService.emitirNfce(venda, false);
        return vendaRepository.save(venda);
    }

    @Transactional(readOnly = true)
    public Page<VendaResponseDTO> listarVendas(LocalDate inicio, LocalDate fim, Pageable pageable) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDateTime.now().minusDays(30);
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();
        return vendaRepository.findByDataVendaBetween(dataInicio, dataFim, pageable)
                .map(v -> VendaResponseDTO.builder()
                        .idVenda(v.getId())
                        .dataVenda(v.getDataVenda())
                        .clienteNome(v.getClienteNome())
                        .clienteDocumento(v.getClienteCpf())
                        .valorTotal(v.getTotalVenda())
                        .desconto(v.getDescontoTotal())
                        .totalItens(v.getItens().size())
                        .statusFiscal(v.getStatusFiscal())
                        .alertas(new ArrayList<>()).build());
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id).orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
    }

    @Transactional
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);
        if (venda.getStatusFiscal() == StatusFiscal.CANCELADA) throw new ValidationException("Venda já cancelada.");

        if (venda.getStatusFiscal() == StatusFiscal.ORCAMENTO || venda.getStatusFiscal() == StatusFiscal.EM_ESPERA) {
            venda.setStatusFiscal(StatusFiscal.CANCELADA);
            venda.setMotivoDoCancelamento(motivo);
            vendaRepository.save(venda);
            return;
        }

        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.CANCELAMENTO_DE_VENDA);
            ajuste.setObservacao("Estorno Venda #" + idVenda);
            estoqueService.realizarAjusteInventario(ajuste);
        });

        venda.setStatusFiscal(StatusFiscal.CANCELADA);
        venda.setMotivoDoCancelamento(motivo);
        vendaRepository.save(venda);
    }

    private BigDecimal processarItensDaVenda(Venda venda, VendaRequestDTO dto) {
        BigDecimal totalAcumulado = BigDecimal.ZERO;
        for (ItemVendaDTO itemDto : dto.itens()) {
            Produto produto;

            if (itemDto.getProdutoId() != null) {
                produto = produtoRepository.findById(itemDto.getProdutoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado ID: " + itemDto.getProdutoId()));
            } else {
                produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado Código: " + itemDto.getCodigoBarras()));
            }

            // CORREÇÃO: Conversão explícita de Integer para BigDecimal
            BigDecimal qtdItem = BigDecimal.valueOf(itemDto.getQuantidade());

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(qtdItem);
            item.setPrecoUnitario(itemDto.getPrecoUnitario() != null ? itemDto.getPrecoUnitario() : produto.getPrecoVenda());
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);

            venda.adicionarItem(item);
            totalAcumulado = totalAcumulado.add(item.getPrecoUnitario().multiply(qtdItem));
        }
        return totalAcumulado;
    }

    private void validarLimitesDeDesconto(Usuario usuario, BigDecimal totalVenda, BigDecimal descontoAplicado) {
        if (descontoAplicado.compareTo(BigDecimal.ZERO) <= 0) return;

        ConfiguracaoLoja config = configuracaoLojaRepository.findById(1L).orElseGet(() -> {
            ConfiguracaoLoja nova = new ConfiguracaoLoja();
            nova.setPercentualMaximoDescontoCaixa(new BigDecimal("10.00"));
            nova.setPercentualMaximoDescontoGerente(new BigDecimal("100.00"));
            return nova;
        });

        BigDecimal percentualAplicado = descontoAplicado.divide(totalVenda, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        BigDecimal limitePermitido = (usuario.getPerfilDoUsuario() == PerfilDoUsuario.ADMIN)
                ? config.getPercentualMaximoDescontoGerente()
                : config.getPercentualMaximoDescontoCaixa();

        if (percentualAplicado.compareTo(limitePermitido) > 0) {
            throw new ValidationException(String.format("Desconto de %.2f%% não autorizado. Seu limite é %.2f%%", percentualAplicado, limitePermitido));
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

    private void executarFluxosOperacionais(Venda venda, VendaRequestDTO dto) {
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA);
            ajuste.setObservacao("Venda #" + venda.getId());
            estoqueService.realizarAjusteInventario(ajuste);
        });

        Long clienteId = venda.getCliente() != null ? venda.getCliente().getId() : null;

        for (PagamentoRequestDTO pag : dto.pagamentos()) {
            financeiroService.lancarReceitaDeVenda(
                    venda.getId(),
                    pag.valor(),
                    pag.formaPagamento().name(),
                    dto.quantidadeParcelas() != null ? dto.quantidadeParcelas() : 1,
                    clienteId
            );
        }
    }

    private Usuario capturarUsuarioLogado() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            if (auth.getPrincipal() instanceof Usuario) return (Usuario) auth.getPrincipal();

            String identificador = null;
            if (auth.getPrincipal() instanceof String) {
                identificador = (String) auth.getPrincipal();
            } else if (auth.getPrincipal() instanceof UserDetails) {
                identificador = ((UserDetails) auth.getPrincipal()).getUsername();
            }

            if (identificador != null) {
                return usuarioRepository.findByMatriculaOrEmail(identificador, identificador)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Erro ao identificar utilizador", e);
        }
        return null;
    }
}
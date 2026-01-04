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
import java.util.*;

@Slf4j
@Service
public class VendaService {

    private static final String NOME_CLIENTE_PADRAO = "Consumidor Final";

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ConfiguracaoLojaRepository configuracaoLojaRepository;

    @Autowired private EstoqueService estoqueService;
    @Autowired private FinanceiroService financeiroService;
    @Autowired private NfceService nfceService;

    // ==================================================================================
    // SESSÃO 1: TRANSAÇÕES DE VENDA (ESCRITA)
    // ==================================================================================

    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();
        if (usuarioLogado == null) {
            throw new ValidationException("Erro de Segurança: Usuário não identificado. Faça login novamente.");
        }

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
                if (venda.getClienteNome() == null || venda.getClienteNome().equalsIgnoreCase(NOME_CLIENTE_PADRAO)) {
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
            boolean temCrediario = dto.pagamentos().stream()
                    .anyMatch(p -> p.formaPagamento() == FormaDePagamento.CREDIARIO);
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
                log.error("NFC-e pendente para venda {}", vendaSalva.getId(), e);
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

        BigDecimal totalItens = processarItensDaVenda(venda, dto);
        venda.setTotalVenda(totalItens);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        return vendaRepository.save(venda);
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

    // ==================================================================================
    // SESSÃO 2: CONSULTAS (LEITURA)
    // ==================================================================================

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<Venda> listarVendasSuspensas() {
        // CORREÇÃO: Usando o nome exato definido no Repository
        return vendaRepository.findByStatusFiscalOrderByDataVendaDesc(StatusFiscal.EM_ESPERA);
    }

    @Transactional(readOnly = true)
    public Page<VendaResponseDTO> listarVendas(LocalDate inicio, LocalDate fim, Pageable pageable) {
        LocalDateTime dIni = (inicio != null) ? inicio.atStartOfDay() : LocalDateTime.now().minusDays(30);
        LocalDateTime dFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();

        return vendaRepository.findByDataVendaBetween(dIni, dFim, pageable)
                .map(v -> VendaResponseDTO.builder()
                        .idVenda(v.getId())
                        .dataVenda(v.getDataVenda())
                        .clienteNome(v.getClienteNome())
                        .valorTotal(v.getTotalVenda())
                        .statusFiscal(v.getStatusFiscal())
                        .build());
    }

    // ==================================================================================
    // SESSÃO 3: CANCELAMENTO
    // ==================================================================================

    @Transactional
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);
        if (venda.getStatusFiscal() == StatusFiscal.CANCELADA) {
            throw new ValidationException("Venda já cancelada.");
        }

        if (venda.getStatusFiscal() != StatusFiscal.ORCAMENTO && venda.getStatusFiscal() != StatusFiscal.EM_ESPERA) {
            venda.getItens().forEach(item -> {
                AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
                ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
                ajuste.setQuantidade(item.getQuantidade());
                ajuste.setMotivo(MotivoMovimentacaoDeEstoque.CANCELAMENTO_DE_VENDA);
                ajuste.setObservacao("Estorno Venda #" + idVenda);
                estoqueService.realizarAjusteInventario(ajuste);
            });
        }

        venda.setStatusFiscal(StatusFiscal.CANCELADA);
        venda.setMotivoDoCancelamento(motivo);
        vendaRepository.save(venda);
    }

    // ==================================================================================
    // SESSÃO 4: PROCESSAMENTO INTERNO E AUXILIARES
    // ==================================================================================

    private BigDecimal processarItensDaVenda(Venda venda, VendaRequestDTO dto) {
        BigDecimal totalAcumulado = BigDecimal.ZERO;

        Set<Long> ids = new HashSet<>();
        Set<String> codigos = new HashSet<>();

        if (dto.itens() != null) {
            for (ItemVendaDTO i : dto.itens()) {
                if (i.getProdutoId() != null) ids.add(i.getProdutoId());
                else if (i.getCodigoBarras() != null) codigos.add(i.getCodigoBarras());
            }
        }

        List<Produto> produtosById = ids.isEmpty() ? Collections.emptyList() : produtoRepository.findAllById(ids);
        List<Produto> produtosByCod = codigos.isEmpty() ? Collections.emptyList() : produtoRepository.findByCodigoBarrasIn(new ArrayList<>(codigos));

        Map<String, Produto> mapProdutos = new HashMap<>();
        produtosById.forEach(p -> mapProdutos.put(p.getId().toString(), p));
        produtosByCod.forEach(p -> mapProdutos.put(p.getCodigoBarras(), p));

        if (dto.itens() != null) {
            for (ItemVendaDTO itemDto : dto.itens()) {
                Produto produto = (itemDto.getProdutoId() != null)
                        ? mapProdutos.get(itemDto.getProdutoId().toString())
                        : mapProdutos.get(itemDto.getCodigoBarras());

                if (produto == null) {
                    throw new ResourceNotFoundException("Produto não encontrado ou indisponível: "
                            + (itemDto.getProdutoId() != null ? itemDto.getProdutoId() : itemDto.getCodigoBarras()));
                }

                BigDecimal qtd = itemDto.getQuantidade();
                ItemVenda item = new ItemVenda();
                item.setProduto(produto);
                item.setQuantidade(qtd);
                item.setPrecoUnitario(itemDto.getPrecoUnitario() != null ? itemDto.getPrecoUnitario() : produto.getPrecoVenda());
                item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);

                venda.adicionarItem(item);
                totalAcumulado = totalAcumulado.add(item.getPrecoUnitario().multiply(qtd));
            }
        }
        return totalAcumulado;
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
        if (dto.pagamentos() != null) {
            for (PagamentoRequestDTO pag : dto.pagamentos()) {
                int parcelas = 1;
                boolean parcelavel = (pag.formaPagamento() == FormaDePagamento.CREDITO || pag.formaPagamento() == FormaDePagamento.CREDIARIO);

                if (parcelavel) {
                    parcelas = dto.quantidadeParcelas() != null ? dto.quantidadeParcelas() : 1;
                }
                financeiroService.lancarReceitaDeVenda(venda.getId(), pag.valor(), pag.formaPagamento().name(), parcelas, clienteId);
            }
        }
    }

    private void validarLimitesDeDesconto(Usuario usuario, BigDecimal total, BigDecimal desconto) {
        if (desconto == null || desconto.compareTo(BigDecimal.ZERO) <= 0) return;
        ConfiguracaoLoja config = configuracaoLojaRepository.findAll().stream().findFirst().orElse(new ConfiguracaoLoja());

        BigDecimal maxCaixa = config.getPercentualMaximoDescontoCaixa() != null ? config.getPercentualMaximoDescontoCaixa() : BigDecimal.TEN;
        BigDecimal maxGerente = config.getPercentualMaximoDescontoGerente() != null ? config.getPercentualMaximoDescontoGerente() : new BigDecimal("100");

        BigDecimal perc = BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            perc = desconto.divide(total, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        BigDecimal limite = (usuario.getPerfilDoUsuario() == PerfilDoUsuario.ADMIN || usuario.getPerfilDoUsuario() == PerfilDoUsuario.ADMIN)
                ? maxGerente : maxCaixa;

        if (perc.compareTo(limite) > 0) {
            throw new ValidationException("Desconto não autorizado. Seu limite é: " + limite + "%");
        }
    }

    private void validarCreditoDoCliente(String doc, BigDecimal valorVenda) {
        if (doc == null || doc.isBlank()) throw new ValidationException("Documento obrigatório para venda no Crediário.");
        Cliente c = clienteRepository.findByDocumento(doc)
                .orElseThrow(() -> new ValidationException("Cliente não cadastrado para crediário."));
        if (!c.isAtivo()) throw new ValidationException("Cliente bloqueado.");

        BigDecimal dividaAtual = contaReceberRepository.somarDividaTotalPorDocumento(doc);
        if (dividaAtual == null) dividaAtual = BigDecimal.ZERO;

        BigDecimal limite = c.getLimiteCredito() != null ? c.getLimiteCredito() : BigDecimal.ZERO;

        if (dividaAtual.add(valorVenda).compareTo(limite) > 0) {
            throw new ValidationException("Limite de crédito excedido. Disponível: R$ " + limite.subtract(dividaAtual));
        }
    }

    private Usuario capturarUsuarioLogado() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            if (auth.getPrincipal() instanceof Usuario) return (Usuario) auth.getPrincipal();
            String idOrEmail = (auth.getPrincipal() instanceof UserDetails)
                    ? ((UserDetails) auth.getPrincipal()).getUsername()
                    : auth.getPrincipal().toString();
            return usuarioRepository.findByMatriculaOrEmail(idOrEmail, idOrEmail).orElse(null);
        } catch (Exception e) {
            log.warn("Não foi possível identificar o usuário logado: {}", e.getMessage());
        }
        return null;
    }
}
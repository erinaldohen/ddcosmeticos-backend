package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
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

    // ==================================================================================
    // SESSÃO 1: DEPENDÊNCIAS
    // ==================================================================================
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
    // SESSÃO 2: OPERAÇÃO PRINCIPAL (REALIZAR VENDA / ORÇAMENTO / SUSPENDER)
    // ==================================================================================

    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();
        if (usuarioLogado == null) throw new ValidationException("Erro crítico: Nenhum usuário identificado.");

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setClienteCpf(dto.clienteCpf());
        // Se não tiver nome e for suspensão, usa um placeholder
        venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Cliente no Balcão");
        venda.setDataVenda(LocalDateTime.now());
        venda.setFormaPagamento(dto.formaPagamento());

        // --- LÓGICA DE STATUS ---
        boolean isOrcamento = Boolean.TRUE.equals(dto.ehOrcamento());

        // Se é orçamento, status ORCAMENTO. Se não, PENDENTE (venda normal).
        // A lógica de SUSPENDER virá em um método específico ou flag, mas podemos tratar aqui se o DTO suportasse.
        // Para simplificar, vou manter este método para Venda e Orçamento, e criar um específico para Suspender abaixo.
        venda.setStatusFiscal(isOrcamento ? StatusFiscal.ORCAMENTO : StatusFiscal.PENDENTE);

        BigDecimal totalItens = processarItensDaVenda(venda, dto);
        venda.setTotalVenda(totalItens);

        BigDecimal desconto = dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO;
        venda.setDescontoTotal(desconto);

        validarLimitesDeDesconto(usuarioLogado, totalItens, desconto);

        BigDecimal valorFinal = venda.getTotalVenda().subtract(venda.getDescontoTotal());
        if (valorFinal.compareTo(BigDecimal.ZERO) < 0) valorFinal = BigDecimal.ZERO;

        if (!isOrcamento && dto.formaPagamento() == FormaDePagamento.CREDIARIO) {
            validarCreditoDoCliente(dto.clienteCpf(), valorFinal);
        }

        Venda vendaSalva = vendaRepository.save(venda);

        if (!isOrcamento) {
            executarFluxosOperacionais(vendaSalva, dto);
            nfceService.emitirNfce(vendaSalva, dto.apenasItensComNfEntrada());
        }

        return vendaRepository.save(vendaSalva);
    }

    // ==================================================================================
    // SESSÃO 3: FILA DE ESPERA (SUSPENDER E RETOMAR) - NOVO
    // ==================================================================================

    @Transactional
    public Venda suspenderVenda(VendaRequestDTO dto) {
        // Lógica similar à venda, mas sem validar pagamento e com status EM_ESPERA
        Usuario usuarioLogado = capturarUsuarioLogado();

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setClienteCpf(dto.clienteCpf());
        venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Venda Suspensa - " + LocalTime.now().toString().substring(0,5));
        venda.setDataVenda(LocalDateTime.now());
        // Forma de pagamento é provisória ou nula na suspensão
        venda.setFormaPagamento(dto.formaPagamento() != null ? dto.formaPagamento() : FormaDePagamento.DINHEIRO);
        venda.setStatusFiscal(StatusFiscal.EM_ESPERA);

        BigDecimal totalItens = processarItensDaVenda(venda, dto);
        venda.setTotalVenda(totalItens);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        // Não executa baixa de estoque nem financeiro. Apenas salva.
        return vendaRepository.save(venda);
    }

    @Transactional(readOnly = true)
    public List<VendaResponseDTO> listarVendasSuspensas() {
        return vendaRepository.findByStatusFiscalOrderByDataVendaDesc(StatusFiscal.EM_ESPERA)
                .stream()
                .map(v -> VendaResponseDTO.builder()
                        .idVenda(v.getId())
                        .dataVenda(v.getDataVenda())
                        .clienteNome(v.getClienteNome()) // Importante mostrar o nome/senha
                        .valorTotal(v.getTotalVenda().subtract(v.getDescontoTotal()))
                        .totalItens(v.getItens().size())
                        .statusFiscal(v.getStatusFiscal())
                        .alertas(new ArrayList<>())
                        .build())
                .collect(Collectors.toList());
    }

    // ==================================================================================
    // SESSÃO 4: EFETIVAÇÃO (ORÇAMENTO OU SUSPENSA)
    // ==================================================================================

    @Transactional
    public Venda efetivarVenda(Long idVendaPrevia) {
        // Serve tanto para ORCAMENTO quanto para EM_ESPERA
        Venda venda = buscarVendaComItens(idVendaPrevia);

        if (venda.getStatusFiscal() != StatusFiscal.ORCAMENTO && venda.getStatusFiscal() != StatusFiscal.EM_ESPERA) {
            throw new ValidationException("Apenas orçamentos ou vendas suspensas podem ser efetivadas.");
        }

        // 1. Atualiza Data para o momento real da venda
        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusFiscal(StatusFiscal.PENDENTE);

        // 2. Revalida Crédito (se for fiado)
        if (venda.getFormaPagamento() == FormaDePagamento.CREDIARIO) {
            BigDecimal valorFinal = venda.getTotalVenda().subtract(venda.getDescontoTotal());
            validarCreditoDoCliente(venda.getClienteCpf(), valorFinal);
        }

        // 3. Executa Fluxos (Baixa Estoque e Financeiro)
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA.name());
            ajuste.setTipoMovimento(TipoMovimentoEstoque.SAIDA.name());
            estoqueService.realizarAjusteInventario(ajuste);
        });

        financeiroService.lancarReceitaDeVenda(
                venda.getId(),
                venda.getTotalVenda().subtract(venda.getDescontoTotal()),
                venda.getFormaPagamento().name(),
                1
        );

        nfceService.emitirNfce(venda, false);

        return vendaRepository.save(venda);
    }

    // ==================================================================================
    // SESSÃO 5: CONSULTAS E AUXILIARES
    // ==================================================================================

    // (Mantido igual ao anterior, apenas para contexto de compilação)
    @Transactional(readOnly = true)
    public Page<VendaResponseDTO> listarVendas(LocalDate inicio, LocalDate fim, Pageable pageable) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDateTime.now().minusDays(30);
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();

        return vendaRepository.findByDataVendaBetween(dataInicio, dataFim, pageable)
                .map(v -> VendaResponseDTO.builder()
                        .idVenda(v.getId())
                        .dataVenda(v.getDataVenda())
                        .clienteNome(v.getClienteNome())
                        .valorTotal(v.getTotalVenda())
                        .desconto(v.getDescontoTotal())
                        .totalItens(v.getItens().size())
                        .statusFiscal(v.getStatusFiscal())
                        .alertas(new ArrayList<>())
                        .build());
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
    }

    @Transactional
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);
        if (venda.getStatusFiscal() == StatusFiscal.CANCELADA) throw new ValidationException("Já cancelada.");

        // Se for pré-venda, só cancela status
        if (venda.getStatusFiscal() == StatusFiscal.ORCAMENTO || venda.getStatusFiscal() == StatusFiscal.EM_ESPERA) {
            venda.setStatusFiscal(StatusFiscal.CANCELADA);
            venda.setMotivoDoCancelamento(motivo);
            vendaRepository.save(venda);
            return;
        }

        // Estornos normais...
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.CANCELAMENTO_DE_VENDA.name());
            ajuste.setTipoMovimento(TipoMovimentoEstoque.ENTRADA.name());
            estoqueService.realizarAjusteInventario(ajuste);
        });
        financeiroService.cancelarReceitaDeVenda(idVenda);
        venda.setStatusFiscal(StatusFiscal.CANCELADA);
        venda.setMotivoDoCancelamento(motivo);
        vendaRepository.save(venda);
    }

    // --- Validações e Métodos Privados (Mantidos iguais) ---
    private void validarLimitesDeDesconto(Usuario usuario, BigDecimal totalVenda, BigDecimal descontoAplicado) {
        if (descontoAplicado.compareTo(BigDecimal.ZERO) <= 0) return;
        ConfiguracaoLoja config = configuracaoLojaRepository.findById(1L).orElseGet(() -> {
            ConfiguracaoLoja nova = new ConfiguracaoLoja();
            nova.setPercentualMaximoDescontoCaixa(new BigDecimal("5.00"));
            nova.setPercentualMaximoDescontoGerente(new BigDecimal("100.00"));
            return nova;
        });
        BigDecimal percentualAplicado = descontoAplicado.divide(totalVenda, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        BigDecimal limitePermitido = (usuario.getPerfil() == PerfilDoUsuario.ROLE_ADMIN || usuario.getPerfil() == PerfilDoUsuario.ROLE_USUARIO)
                ? config.getPercentualMaximoDescontoGerente()
                : config.getPercentualMaximoDescontoCaixa();
        if (percentualAplicado.compareTo(limitePermitido) > 0) {
            throw new ValidationException(String.format("Desconto não autorizado. Aplicado: %.2f%% | Limite: %.2f%%", percentualAplicado, limitePermitido));
        }
    }

    private BigDecimal processarItensDaVenda(Venda venda, VendaRequestDTO dto) {
        BigDecimal totalAcumulado = BigDecimal.ZERO;
        for (ItemVendaDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDto.getCodigoBarras()));

            // Valida se tem estoque físico, mesmo para suspensão (para não prometer o que não tem)
            BigDecimal estoqueAtual = BigDecimal.valueOf(produto.getQuantidadeEmEstoque());
            if (estoqueAtual.compareTo(itemDto.getQuantidade()) < 0) {
                throw new ValidationException("Estoque insuficiente para: " + produto.getDescricao());
            }

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);
            venda.adicionarItem(item);
            totalAcumulado = totalAcumulado.add(item.getPrecoUnitario().multiply(item.getQuantidade()));
        }
        return totalAcumulado;
    }

    private void validarCreditoDoCliente(String cpf, BigDecimal valorDaCompra) {
        if (cpf == null || cpf.isBlank()) throw new ValidationException("CPF obrigatório para Crediário.");
        Cliente cliente = clienteRepository.findByCpf(cpf).orElseThrow(() -> new ValidationException("Cliente não cadastrado."));
        if (!cliente.isAtivo()) throw new ValidationException("Cliente bloqueado.");
        if (contaReceberRepository.existeContaVencida(cpf, LocalDate.now())) throw new ValidationException("Cliente com contas vencidas.");
        BigDecimal dividaAtual = contaReceberRepository.somarDividaTotalPorCpf(cpf);
        if (dividaAtual == null) dividaAtual = BigDecimal.ZERO;
        if (dividaAtual.add(valorDaCompra).compareTo(cliente.getLimiteCredito()) > 0) throw new ValidationException("Limite de Crédito Excedido.");
    }

    private void executarFluxosOperacionais(Venda venda, VendaRequestDTO dto) {
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA.name());
            ajuste.setTipoMovimento(TipoMovimentoEstoque.SAIDA.name());
            estoqueService.realizarAjusteInventario(ajuste);
        });
        financeiroService.lancarReceitaDeVenda(venda.getId(), venda.getTotalVenda().subtract(venda.getDescontoTotal()), dto.formaPagamento().name(), dto.quantidadeParcelas());
    }

    private Usuario capturarUsuarioLogado() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            if (auth.getPrincipal() instanceof Usuario) return (Usuario) auth.getPrincipal();
            if (auth.getPrincipal() instanceof UserDetails) return usuarioRepository.findByMatricula(((UserDetails) auth.getPrincipal()).getUsername()).orElse(null);
            if (auth.getPrincipal() instanceof String) return usuarioRepository.findByMatricula((String) auth.getPrincipal()).orElse(null);
        } catch (Exception e) {
            log.warn("Erro ao identificar usuário", e);
        }
        return null;
    }
}
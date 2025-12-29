package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque; // IMPORT ADICIONADO
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
    // SESSÃO 2: OPERAÇÃO PRINCIPAL (REALIZAR VENDA)
    // ==================================================================================

    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto) {
        // 1. Identificação e Auditoria
        Usuario usuarioLogado = capturarUsuarioLogado();
        if (usuarioLogado == null) throw new ValidationException("Erro crítico: Nenhum usuário identificado.");

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setClienteCpf(dto.clienteCpf());
        venda.setClienteNome(dto.clienteNome());
        venda.setDataVenda(LocalDateTime.now());
        venda.setFormaPagamento(dto.formaPagamento());
        venda.setStatusFiscal(StatusFiscal.PENDENTE);

        // 2. Processamento de Itens e Estoque
        BigDecimal totalItens = processarItensDaVenda(venda, dto);
        venda.setTotalVenda(totalItens);

        BigDecimal desconto = dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO;
        venda.setDescontoTotal(desconto);

        // 3. Validação de Segurança (Limites de Desconto)
        validarLimitesDeDesconto(usuarioLogado, totalItens, desconto);

        // O valor final a pagar (Total - Desconto)
        BigDecimal valorFinal = venda.getTotalVenda().subtract(venda.getDescontoTotal());
        if (valorFinal.compareTo(BigDecimal.ZERO) < 0) valorFinal = BigDecimal.ZERO;

        // 4. Validação Financeira (Fiado/Crediário)
        if (dto.formaPagamento() == FormaDePagamento.CREDIARIO) {
            validarCreditoDoCliente(dto.clienteCpf(), valorFinal);
        }

        // 5. Persistência
        Venda vendaSalva = vendaRepository.save(venda);

        // 6. Pós-Processamento (Baixa de Estoque e Geração de Títulos)
        executarFluxosOperacionais(vendaSalva, dto);

        // 7. Emissão Fiscal (Simulada)
        nfceService.emitirNfce(vendaSalva, dto.apenasItensComNfEntrada());

        return vendaRepository.save(vendaSalva);
    }

    // ==================================================================================
    // SESSÃO 3: CONSULTAS E RELATÓRIOS
    // ==================================================================================

    @Transactional(readOnly = true)
    public Page<VendaResponseDTO> listarVendas(LocalDate inicio, LocalDate fim, Pageable pageable) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDateTime.now().minusDays(30);
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();

        return vendaRepository.findByDataVendaBetween(dataInicio, dataFim, pageable)
                .map(v -> VendaResponseDTO.builder()
                        .idVenda(v.getId())
                        .dataVenda(v.getDataVenda())
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

    // ==================================================================================
    // SESSÃO 4: GESTÃO E CANCELAMENTO
    // ==================================================================================

    @Transactional
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);

        if (venda.getStatusFiscal() == StatusFiscal.CANCELADA) {
            throw new ValidationException("Esta venda já está cancelada.");
        }

        log.info("Cancelando Venda #{}. Motivo: {}", idVenda, motivo);

        // Estorno de Estoque
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.CANCELAMENTO_DE_VENDA.name());

            // CORREÇÃO: Usando Enum para garantir tipo correto (ENTRADA no estoque)
            ajuste.setTipoMovimento(TipoMovimentoEstoque.ENTRADA.name());

            estoqueService.realizarAjusteInventario(ajuste);
        });

        // Estorno Financeiro
        financeiroService.cancelarReceitaDeVenda(idVenda);

        venda.setStatusFiscal(StatusFiscal.CANCELADA);
        venda.setMotivoDoCancelamento(motivo);
        vendaRepository.save(venda);
    }

    // ==================================================================================
    // SESSÃO 5: VALIDAÇÕES DE SEGURANÇA
    // ==================================================================================

    private void validarLimitesDeDesconto(Usuario usuario, BigDecimal totalVenda, BigDecimal descontoAplicado) {
        if (descontoAplicado.compareTo(BigDecimal.ZERO) <= 0) return;

        // Busca configurações (se não existir, cria padrão)
        ConfiguracaoLoja config = configuracaoLojaRepository.findById(1L).orElseGet(() -> {
            ConfiguracaoLoja nova = new ConfiguracaoLoja();
            nova.setPercentualMaximoDescontoCaixa(new BigDecimal("5.00"));
            nova.setPercentualMaximoDescontoGerente(new BigDecimal("100.00")); // Gerente livre por padrão
            return nova;
        });

        // Calcula percentual aplicado
        BigDecimal percentualAplicado = descontoAplicado
                .divide(totalVenda, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        BigDecimal limitePermitido;

        // Verifica Perfil
        // Nota: Ajustado conforme o seu código original para ROLE_ADMIN/ROLE_USUARIO
        if (usuario.getPerfil() == PerfilDoUsuario.ROLE_ADMIN || usuario.getPerfil() == PerfilDoUsuario.ROLE_USUARIO) {
            limitePermitido = config.getPercentualMaximoDescontoGerente();
        } else {
            limitePermitido = config.getPercentualMaximoDescontoCaixa();
        }

        if (percentualAplicado.compareTo(limitePermitido) > 0) {
            throw new ValidationException(String.format(
                    "Desconto não autorizado para seu perfil. Aplicado: %.2f%% | Limite: %.2f%%",
                    percentualAplicado, limitePermitido
            ));
        }
    }

    // ==================================================================================
    // SESSÃO 6: MÉTODOS AUXILIARES PRIVADOS
    // ==================================================================================

    private BigDecimal processarItensDaVenda(Venda venda, VendaRequestDTO dto) {
        BigDecimal totalAcumulado = BigDecimal.ZERO;

        for (ItemVendaDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDto.getCodigoBarras()));

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
        if (cpf == null || cpf.isBlank()) {
            throw new ValidationException("CPF obrigatório para Crediário.");
        }

        Cliente cliente = clienteRepository.findByCpf(cpf)
                .orElseThrow(() -> new ValidationException("Cliente não cadastrado."));

        if (!cliente.isAtivo()) throw new ValidationException("Cliente bloqueado.");

        boolean temAtraso = contaReceberRepository.existeContaVencida(cpf, LocalDate.now());
        if (temAtraso) throw new ValidationException("Venda Bloqueada: Cliente com contas vencidas.");

        BigDecimal dividaAtual = contaReceberRepository.somarDividaTotalPorCpf(cpf);
        if (dividaAtual == null) dividaAtual = BigDecimal.ZERO;

        if (dividaAtual.add(valorDaCompra).compareTo(cliente.getLimiteCredito()) > 0) {
            throw new ValidationException("Limite de Crédito Excedido.");
        }
    }

    private void executarFluxosOperacionais(Venda venda, VendaRequestDTO dto) {
        // Baixa Estoque
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA.name());

            // CORREÇÃO: Usando Enum para garantir tipo correto (SAIDA do estoque)
            ajuste.setTipoMovimento(TipoMovimentoEstoque.SAIDA.name());

            estoqueService.realizarAjusteInventario(ajuste);
        });

        // Financeiro
        financeiroService.lancarReceitaDeVenda(
                venda.getId(),
                venda.getTotalVenda().subtract(venda.getDescontoTotal()),
                dto.formaPagamento().name(),
                dto.quantidadeParcelas()
        );
    }

    private Usuario capturarUsuarioLogado() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            if (auth.getPrincipal() instanceof Usuario) return (Usuario) auth.getPrincipal();

            String login = null;
            if (auth.getPrincipal() instanceof UserDetails) login = ((UserDetails) auth.getPrincipal()).getUsername();
            else if (auth.getPrincipal() instanceof String) login = (String) auth.getPrincipal();

            if (login != null) return usuarioRepository.findByMatricula(login).orElse(null);
        } catch (Exception e) {
            log.warn("Erro ao identificar usuário", e);
        }
        return null;
    }
}
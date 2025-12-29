package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;

@Slf4j
@Service
public class VendaService {

    // --- DEPENDÊNCIAS ---
    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository; // Novo: Para validação de crédito
    @Autowired private ContaReceberRepository contaReceberRepository; // Novo: Para verificar dívidas

    @Autowired private EstoqueService estoqueService;
    @Autowired private FinanceiroService financeiroService;
    @Autowired private NfceService nfceService;

    // ==================================================================================
    // SESSÃO 1: OPERAÇÃO PRINCIPAL (REALIZAR VENDA)
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
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        // O valor final a pagar (Total - Desconto)
        BigDecimal valorFinal = venda.getTotalVenda().subtract(venda.getDescontoTotal());
        if (valorFinal.compareTo(BigDecimal.ZERO) < 0) valorFinal = BigDecimal.ZERO;

        // 3. Validação Financeira (NOVO: Lógica do Fiado)
        if (dto.formaPagamento() == FormaDePagamento.CREDIARIO) {
            validarCreditoDoCliente(dto.clienteCpf(), valorFinal);
        }

        // 4. Persistência
        Venda vendaSalva = vendaRepository.save(venda);

        // 5. Pós-Processamento (Baixa de Estoque e Geração de Títulos)
        executarFluxosOperacionais(vendaSalva, dto);

        // 6. Emissão Fiscal (Assíncrona/Simulada)
        nfceService.emitirNfce(vendaSalva, dto.apenasItensComNfEntrada());

        return vendaRepository.save(vendaSalva);
    }

    // ==================================================================================
    // SESSÃO 2: CONSULTAS E RELATÓRIOS
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
                        .alertas(new ArrayList<>()) // Futuro: Alertas de margem baixa
                        .build());
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
    }

    // ==================================================================================
    // SESSÃO 3: GESTÃO E CANCELAMENTO
    // ==================================================================================

    @Transactional
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);

        if (venda.getStatusFiscal() == StatusFiscal.CANCELADA) {
            throw new ValidationException("Esta venda já está cancelada.");
        }

        log.info("Cancelando Venda #{}. Motivo: {}", idVenda, motivo);

        // 1. Estorno de Estoque (Devolve produtos para a loja)
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.CANCELAMENTO_DE_VENDA.name());
            ajuste.setTipoMovimento("ENTRADA");

            estoqueService.realizarAjusteInventario(ajuste);
        });

        // 2. Estorno Financeiro (Cancela parcelas/fiado)
        financeiroService.cancelarReceitaDeVenda(idVenda);

        // 3. Atualização de Status
        venda.setStatusFiscal(StatusFiscal.CANCELADA);
        venda.setMotivoDoCancelamento(motivo);
        vendaRepository.save(venda);
    }

    // ==================================================================================
    // SESSÃO 4: MÉTODOS AUXILIARES PRIVADOS
    // ==================================================================================

    /**
     * Processa cada item, valida estoque e calcula o subtotal bruto.
     */
    private BigDecimal processarItensDaVenda(Venda venda, VendaRequestDTO dto) {
        BigDecimal totalAcumulado = BigDecimal.ZERO;

        for (ItemVendaDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDto.getCodigoBarras()));

            // Validação de Estoque Físico
            BigDecimal estoqueAtual = BigDecimal.valueOf(produto.getQuantidadeEmEstoque());
            if (estoqueAtual.compareTo(itemDto.getQuantidade()) < 0) {
                throw new ValidationException("Estoque insuficiente para o produto: " + produto.getDescricao() +
                        ". Disponível: " + estoqueAtual);
            }

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());
            // Registra o custo do momento da venda para relatórios de lucro futuro
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);

            venda.adicionarItem(item);
            totalAcumulado = totalAcumulado.add(item.getPrecoUnitario().multiply(item.getQuantidade()));
        }
        return totalAcumulado;
    }

    /**
     * Valida as regras de negócio para venda fiado (Crediário).
     */
    private void validarCreditoDoCliente(String cpf, BigDecimal valorDaCompra) {
        if (cpf == null || cpf.isBlank()) {
            throw new ValidationException("CPF do cliente é obrigatório para venda no Crediário.");
        }

        // 1. Busca Cadastro
        Cliente cliente = clienteRepository.findByCpf(cpf)
                .orElseThrow(() -> new ValidationException("Cliente não cadastrado. É necessário cadastrar o cliente antes de vender fiado."));

        if (!cliente.isAtivo()) {
            throw new ValidationException("Cliente bloqueado/inativo no sistema.");
        }

        // 2. Verifica Contas Atrasadas (Bloqueio Total)
        boolean temAtraso = contaReceberRepository.existeContaVencida(cpf, LocalDate.now());
        if (temAtraso) {
            throw new ValidationException("VENDA BLOQUEADA: O cliente possui contas vencidas em aberto.");
        }

        // 3. Verifica Limite de Crédito
        BigDecimal dividaAtual = contaReceberRepository.somarDividaTotalPorCpf(cpf);
        if (dividaAtual == null) dividaAtual = BigDecimal.ZERO;

        BigDecimal novoTotal = dividaAtual.add(valorDaCompra);

        if (novoTotal.compareTo(cliente.getLimiteCredito()) > 0) {
            throw new ValidationException(String.format(
                    "LIMITE EXCEDIDO. Limite: R$ %.2f | Dívida Atual: R$ %.2f | Esta Compra: R$ %.2f",
                    cliente.getLimiteCredito(), dividaAtual, valorDaCompra
            ));
        }
    }

    /**
     * Efetiva a saída do estoque e o lançamento no financeiro.
     */
    private void executarFluxosOperacionais(Venda venda, VendaRequestDTO dto) {
        // Baixa de Estoque
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA.name());
            ajuste.setTipoMovimento("SAIDA");
            estoqueService.realizarAjusteInventario(ajuste);
        });

        // Lançamento Financeiro
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

            if (auth.getPrincipal() instanceof Usuario) {
                return (Usuario) auth.getPrincipal();
            }

            String login = null;
            if (auth.getPrincipal() instanceof UserDetails) {
                login = ((UserDetails) auth.getPrincipal()).getUsername();
            } else if (auth.getPrincipal() instanceof String) {
                login = (String) auth.getPrincipal();
            }

            if (login != null) {
                return usuarioRepository.findByMatricula(login).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Erro ao identificar usuário logado", e);
        }
        return null;
    }
}
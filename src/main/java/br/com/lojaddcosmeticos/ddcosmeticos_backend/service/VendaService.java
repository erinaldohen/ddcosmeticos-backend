package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
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

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EstoqueService estoqueService;
    @Autowired private FinanceiroService financeiroService;
    @Autowired private NfceService nfceService;
    @Autowired private UsuarioRepository usuarioRepository;

    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();
        if (usuarioLogado == null) throw new ValidationException("Usuário não identificado.");

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setClienteCpf(dto.clienteCpf());
        venda.setClienteNome(dto.clienteNome());
        venda.setDataVenda(LocalDateTime.now());
        venda.setFormaPagamento(dto.formaPagamento());
        venda.setStatusFiscal(StatusFiscal.PENDENTE);

        BigDecimal totalVenda = BigDecimal.ZERO;

        for (ItemVendaDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDto.getCodigoBarras()));

            if (BigDecimal.valueOf(produto.getQuantidadeEmEstoque()).compareTo(itemDto.getQuantidade()) < 0) {
                throw new ValidationException("Estoque insuficiente: " + produto.getDescricao());
            }

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);

            venda.adicionarItem(item);
            totalVenda = totalVenda.add(item.getPrecoUnitario().multiply(item.getQuantidade()));
        }

        venda.setTotalVenda(totalVenda);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        Venda vendaSalva = vendaRepository.save(venda);

        // Baixa Estoque e Gera Financeiro
        executarFluxosOperacionais(vendaSalva, dto);

        // Emissão Fiscal (Simulada em Dev)
        nfceService.emitirNfce(vendaSalva, dto.apenasItensComNfEntrada());

        return vendaRepository.save(vendaSalva);
    }

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

    @Transactional
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);

        if (venda.getStatusFiscal() == StatusFiscal.CANCELADA) {
            throw new ValidationException("Venda já cancelada.");
        }

        log.info("Cancelando Venda #{}. Motivo: {}", idVenda, motivo);

        // 1. Estorno de Estoque (Entrada)
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            // Usa motivo adequado para retorno
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.CANCELAMENTO_DE_VENDA.name());
            ajuste.setTipoMovimento("ENTRADA");
            estoqueService.realizarAjusteInventario(ajuste);
        });

        // 2. Estorno Financeiro
        financeiroService.cancelarReceitaDeVenda(idVenda);

        // 3. Atualiza Status
        venda.setStatusFiscal(StatusFiscal.CANCELADA);
        venda.setMotivoDoCancelamento(motivo);
        vendaRepository.save(venda);
    }

    private void executarFluxosOperacionais(Venda venda, VendaRequestDTO dto) {
        // Baixa Estoque
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA.name());
            ajuste.setTipoMovimento("SAIDA");
            estoqueService.realizarAjusteInventario(ajuste);
        });

        // Lança Financeiro
        financeiroService.lancarReceitaDeVenda(
                venda.getId(),
                venda.getTotalVenda(),
                dto.formaPagamento().name(),
                dto.quantidadeParcelas()
        );
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
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
            log.warn("Erro ao identificar usuário: {}", e.getMessage());
        }
        return null;
    }
}
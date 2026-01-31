package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record VendaResponseDTO(
        Long id,
        LocalDateTime dataVenda,
        BigDecimal valorTotal,
        BigDecimal descontoTotal,
        String clienteNome,
        FormaDePagamento formaDePagamento,
        List<ItemVendaResponseDTO> itens,
        List<PagamentoResponseDTO> pagamentos,
        BigDecimal valorIbs,
        BigDecimal valorCbs,
        BigDecimal valorIs,
        BigDecimal valorTotalImpostos,
        StatusFiscal status,
        String observacao
) {
    // Construtor auxiliar que aceita a Entidade Venda
    public VendaResponseDTO(Venda venda) {
        this(
                venda.getIdVenda(),
                venda.getDataVenda(),
                venda.getValorTotal(),
                venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO,
                venda.getClienteNome(),
                venda.getFormaDePagamento(),
                // Mapeia os itens usando o construtor do ItemVendaResponseDTO
                venda.getItens() != null
                        ? venda.getItens().stream().map(ItemVendaResponseDTO::new).collect(Collectors.toList())
                        : List.of(),
                // Mapeia os pagamentos usando o construtor do PagamentoResponseDTO
                venda.getPagamentos() != null
                        ? venda.getPagamentos().stream().map(PagamentoResponseDTO::new).collect(Collectors.toList())
                        : List.of(),
                venda.getValorIbs() != null ? venda.getValorIbs() : BigDecimal.ZERO,
                venda.getValorCbs() != null ? venda.getValorCbs() : BigDecimal.ZERO,
                venda.getValorIs() != null ? venda.getValorIs() : BigDecimal.ZERO,
                // Soma total de impostos
                (venda.getValorIbs() != null ? venda.getValorIbs() : BigDecimal.ZERO)
                        .add(venda.getValorCbs() != null ? venda.getValorCbs() : BigDecimal.ZERO)
                        .add(venda.getValorIs() != null ? venda.getValorIs() : BigDecimal.ZERO),
                venda.getStatusNfce(),
                venda.getObservacao()
        );
    }
}
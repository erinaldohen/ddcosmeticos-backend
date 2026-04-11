package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record HistoricoCompraDTO(
        Long id,
        LocalDateTime dataVenda,
        String statusNfce,
        BigDecimal valorTotal,
        List<ItemHistoricoDTO> itens
) {
    public HistoricoCompraDTO(Venda venda) {
        this(
                venda.getIdVenda(),
                venda.getDataVenda() != null ? venda.getDataVenda() : LocalDateTime.now(),
                venda.getStatusNfce() != null ? venda.getStatusNfce().name() : "PENDENTE",
                venda.getValorTotal() != null ? venda.getValorTotal() : BigDecimal.ZERO,
                venda.getItens() != null ? venda.getItens().stream().map(ItemHistoricoDTO::new).toList() : List.of()
        );
    }
}
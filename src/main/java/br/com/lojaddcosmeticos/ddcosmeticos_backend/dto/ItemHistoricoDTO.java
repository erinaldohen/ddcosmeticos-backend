package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import java.math.BigDecimal;

public record ItemHistoricoDTO(
        BigDecimal quantidade,
        String produtoNome,
        BigDecimal precoUnitario
) {
    public ItemHistoricoDTO(ItemVenda item) {
        this(
                item.getQuantidade() != null ? item.getQuantidade() : BigDecimal.ZERO,
                item.getProduto() != null ? item.getProduto().getDescricao() : "Produto Excluído/Desconhecido",
                item.getPrecoUnitario() != null ? item.getPrecoUnitario() : BigDecimal.ZERO
        );
    }
}
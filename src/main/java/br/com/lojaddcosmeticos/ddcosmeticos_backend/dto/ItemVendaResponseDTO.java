package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import java.io.Serializable;
import java.math.BigDecimal;

public record ItemVendaResponseDTO(
        // Adicionado ID para o Frontend conseguir manipular o item
        Long produtoId,

        String codigoBarras,

        // Padronizado para facilitar o frontend
        String produtoDescricao,

        BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal desconto, // Nome simplificado
        BigDecimal valorTotalItem,

        // CAMPOS PARA RELATÓRIOS (CMV)
        BigDecimal custoUnitario,
        BigDecimal custoTotal
) implements Serializable {

    public ItemVendaResponseDTO(ItemVenda item) {
        this(
                item.getProduto().getId(),
                item.getProduto().getCodigoBarras(),
                item.getProduto().getDescricao(),
                item.getQuantidade(),
                item.getPrecoUnitario(),

                // 1. Recupera o Desconto Real (Evita Null)
                item.getDesconto() != null ? item.getDesconto() : BigDecimal.ZERO,

                // 2. Calcula Total Líquido: (Preço * Qtd) - Desconto
                (item.getPrecoUnitario().multiply(item.getQuantidade()))
                        .subtract(item.getDesconto() != null ? item.getDesconto() : BigDecimal.ZERO),

                // 3. Custo Unitário (Historico salvo na venda)
                item.getCustoUnitarioHistorico() != null ? item.getCustoUnitarioHistorico() : BigDecimal.ZERO,

                // 4. Custo Total
                (item.getCustoUnitarioHistorico() != null)
                        ? item.getCustoUnitarioHistorico().multiply(item.getQuantidade())
                        : BigDecimal.ZERO
        );
    }
}
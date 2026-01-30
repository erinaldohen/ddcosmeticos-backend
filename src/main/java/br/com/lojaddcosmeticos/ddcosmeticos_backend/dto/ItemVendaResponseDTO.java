package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import java.io.Serializable;
import java.math.BigDecimal;

public record ItemVendaResponseDTO(
        String codigoBarras,
        String descricaoProduto,
        BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal descontoItem,
        BigDecimal valorTotalItem,

        // CAMPOS PARA CMV (Custo da Mercadoria Vendida)
        BigDecimal custoUnitario,
        BigDecimal custoTotal
) implements Serializable {

    /**
     * Construtor auxiliar para facilitar a conversão a partir da Entidade.
     * CORREÇÃO: Realizamos o cálculo matemático aqui (Preço x Qtd) em vez de chamar método inexistente.
     */
    public ItemVendaResponseDTO(ItemVenda item) {
        this(
                item.getProduto().getCodigoBarras(),
                item.getProduto().getDescricao(),
                item.getQuantidade(),
                item.getPrecoUnitario(),

                // Desconto (assumindo zero por enquanto)
                BigDecimal.ZERO,

                // [CORREÇÃO LINHA 34] Calcula Total: Preço Unitário * Quantidade
                (item.getPrecoUnitario() != null && item.getQuantidade() != null)
                        ? item.getPrecoUnitario().multiply(item.getQuantidade())
                        : BigDecimal.ZERO,

                // Custo Unitário
                item.getCustoUnitarioHistorico() != null ? item.getCustoUnitarioHistorico() : BigDecimal.ZERO,

                // [CORREÇÃO LINHA 37] Calcula Custo Total: Custo Unitário * Quantidade
                (item.getCustoUnitarioHistorico() != null && item.getQuantidade() != null)
                        ? item.getCustoUnitarioHistorico().multiply(item.getQuantidade())
                        : BigDecimal.ZERO
        );
    }
}
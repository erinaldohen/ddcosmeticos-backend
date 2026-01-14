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

        // CAMPOS PARA CMV
        BigDecimal custoUnitario,
        BigDecimal custoTotal
) implements Serializable {

    /**
     * Construtor auxiliar para facilitar a conversão a partir da Entidade.
     * Mantém a lógica de assumir desconto ZERO e usar métodos da entidade.
     */
    public ItemVendaResponseDTO(ItemVenda item) {
        this(
                item.getProduto().getCodigoBarras(),
                // Verifica se é getDescricao() ou getNome() na sua entidade Produto
                item.getProduto().getDescricao(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                // Campo desconto não existe na entidade ItemVenda atual, assumindo ZERO
                BigDecimal.ZERO,
                // Usa o método calculado da entidade
                item.getTotalItem(),
                // Campos de custo histórico
                item.getCustoUnitarioHistorico(),
                item.getCustoTotal()
        );
    }
}
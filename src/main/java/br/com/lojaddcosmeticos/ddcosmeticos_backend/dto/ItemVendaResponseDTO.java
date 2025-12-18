package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ItemVendaResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String codigoBarras;
    private String descricaoProduto;
    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal descontoItem;
    private BigDecimal valorTotalItem;

    // CAMPOS PARA CMV
    private BigDecimal custoUnitario;
    private BigDecimal custoTotal;

    public ItemVendaResponseDTO(ItemVenda item) {
        this.codigoBarras = item.getProduto().getCodigoBarras();
        this.descricaoProduto = item.getProduto().getDescricao();
        this.quantidade = item.getQuantidade();
        this.precoUnitario = item.getPrecoUnitario();
        this.descontoItem = item.getDescontoItem() != null ? item.getDescontoItem() : BigDecimal.ZERO;
        this.valorTotalItem = item.getValorTotalItem();

        // CORREÇÃO DA LINHA 37: O nome do campo na entidade é custoUnitarioHistorico
        this.custoUnitario = item.getCustoUnitarioHistorico();

        // O método getCustoTotal() na entidade calcula: quantidade * custoUnitarioHistorico
        this.custoTotal = item.getCustoTotal();
    }
}
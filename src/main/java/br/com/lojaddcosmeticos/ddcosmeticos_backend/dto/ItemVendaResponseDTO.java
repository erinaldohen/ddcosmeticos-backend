// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/ItemVendaResponseDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO para representar um item de venda em uma consulta de transação.
 */
@Data
public class ItemVendaResponseDTO {

    private String codigoBarras;
    private String descricaoProduto;
    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal descontoItem;
    private BigDecimal valorTotalItem;

    // NOVOS CAMPOS PARA CMV
    private BigDecimal custoUnitario;
    private BigDecimal custoTotal;

    public ItemVendaResponseDTO(ItemVenda item) {
        this.codigoBarras = item.getProduto().getCodigoBarras();
        this.descricaoProduto = item.getProduto().getDescricao();
        this.quantidade = item.getQuantidade();
        this.precoUnitario = item.getPrecoUnitario();
        this.descontoItem = item.getDescontoItem();
        this.valorTotalItem = item.getValorTotalItem();

        this.custoUnitario = item.getCustoUnitario();
        this.custoTotal = item.getCustoTotal();
    }
}
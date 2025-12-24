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

        // Verifica se é getDescricao() ou getNome() dependendo da sua versão do Produto
        // Mantive getDescricao() pois parece ser o que você está usando
        this.descricaoProduto = item.getProduto().getDescricao();

        this.quantidade = item.getQuantidade();
        this.precoUnitario = item.getPrecoUnitario();

        // --- CORREÇÃO LINHA 31 ---
        // Como não temos o campo desconto na entidade ItemVenda, assumimos ZERO.
        // Se você quiser desconto por item, precisará adicionar o campo na Entidade primeiro.
        this.descontoItem = BigDecimal.ZERO;

        // --- CORREÇÃO LINHA 32 ---
        // O método que calcula (Preço x Qtde) na entidade se chama getTotalItem()
        this.valorTotalItem = item.getTotalItem();

        // O nome do campo na entidade é custoUnitarioHistorico
        this.custoUnitario = item.getCustoUnitarioHistorico();

        // O método getCustoTotal() na entidade calcula: quantidade * custoUnitarioHistorico
        this.custoTotal = item.getCustoTotal();
    }
}
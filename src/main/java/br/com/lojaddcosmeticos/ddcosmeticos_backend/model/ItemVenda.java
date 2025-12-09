// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/model/ItemVenda.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
@Table(name = "item_venda")
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "quantidade", precision = 10, scale = 3, nullable = false)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", precision = 10, scale = 2, nullable = false)
    private BigDecimal precoUnitario;

    @Column(name = "desconto_item", precision = 10, scale = 2, nullable = false)
    private BigDecimal descontoItem;

    @Column(name = "valor_total_item", precision = 10, scale = 2, nullable = false)
    private BigDecimal valorTotalItem;

    /**
     * Custo da Mercadoria Vendida (CMV) unitário.
     * Corresponde ao Preço Médio Ponderado (PMP) no momento da venda.
     */
    @Column(name = "custo_unitario", precision = 10, scale = 4, nullable = false)
    private BigDecimal custoUnitario;

    /**
     * Custo total do item (CMV Unitário * Quantidade).
     */
    @Column(name = "custo_total", precision = 10, scale = 4, nullable = false)
    private BigDecimal custoTotal;
}
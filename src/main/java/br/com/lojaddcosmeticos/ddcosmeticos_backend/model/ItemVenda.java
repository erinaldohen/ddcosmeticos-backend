// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/model/ItemVenda.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Entidade que representa um item dentro de uma Venda.
 * Mapeia para a tabela 'item_venda' no banco de dados.
 */
@Data
@Entity
@Table(name = "item_venda")
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Relacionamento N:1 com a Venda (Chave estrangeira 'venda_id').
     * FetchType.LAZY: Carrega o objeto Venda apenas quando for acessado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    /**
     * Relacionamento N:1 com o Produto (Chave estrangeira 'produto_id').
     * FetchType.EAGER: Carrega o Produto junto com o ItemVenda (comum em listagens).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    /**
     * Quantidade do produto vendida.
     */
    @Column(name = "quantidade", precision = 10, scale = 3, nullable = false)
    private BigDecimal quantidade;

    /**
     * Preço unitário de venda no momento da transação.
     */
    @Column(name = "preco_unitario", precision = 10, scale = 2, nullable = false)
    private BigDecimal precoUnitario;

    /**
     * Desconto aplicado especificamente ao item.
     */
    @Column(name = "desconto_item", precision = 10, scale = 2, nullable = false)
    private BigDecimal descontoItem = BigDecimal.ZERO;

    /**
     * Valor total do item após o desconto (Preço Unitário * Quantidade - Desconto).
     */
    @Column(name = "valor_total_item", precision = 10, scale = 2, nullable = false)
    private BigDecimal valorTotalItem;
}
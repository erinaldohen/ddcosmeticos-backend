package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "item_venda")
public class ItemVenda {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venda_id")
    private Venda venda;

    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal descontoItem = BigDecimal.ZERO;

    // CAMPO CRÍTICO PARA AUDITORIA E LUCRO
    @Column(name = "custo_unitario_historico")
    private BigDecimal custoUnitarioHistorico;

    @Column(name = "valor_total_item")
    private BigDecimal valorTotalItem;

    // Método auxiliar para o DTO
    public BigDecimal getCustoTotal() {
        if (this.custoUnitarioHistorico == null || this.quantidade == null) {
            return BigDecimal.ZERO;
        }
        return this.custoUnitarioHistorico.multiply(this.quantidade);
    }
}
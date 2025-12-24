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

    // Quantidade agora é BigDecimal para aceitar fracionados (e alinhar com o DTO)
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "custo_unitario_historico", precision = 10, scale = 4)
    private BigDecimal custoUnitarioHistorico;

    @ManyToOne
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    // --- MÉTODOS CALCULADOS (CORREÇÃO DO ERRO) ---

    // Total da Venda deste item (Preço x Qtde)
    public BigDecimal getTotalItem() {
        if (precoUnitario == null || quantidade == null) return BigDecimal.ZERO;
        return precoUnitario.multiply(quantidade);
    }

    // Total de Custo deste item (Custo Histórico x Qtde)
    // ESTE É O MÉTODO QUE FALTAVA
    public BigDecimal getCustoTotal() {
        if (custoUnitarioHistorico == null || quantidade == null) return BigDecimal.ZERO;
        return custoUnitarioHistorico.multiply(quantidade);
    }

    // Lucro Bruto deste item
    public BigDecimal getLucroItem() {
        return getTotalItem().subtract(getCustoTotal());
    }
}
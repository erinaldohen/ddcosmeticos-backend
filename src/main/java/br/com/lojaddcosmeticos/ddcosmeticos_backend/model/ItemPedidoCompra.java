package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
public class ItemPedidoCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pedido_compra_id")
    private PedidoCompra pedidoCompra;

    @ManyToOne
    private Produto produto;

    private BigDecimal quantidade;
    private BigDecimal precoUnitarioTabela; // Preço que o vendedor te passou

    // --- Memória de Cálculo Fiscal ---
    private BigDecimal mvaAplicada; // Margem de Valor Agregado usada
    private BigDecimal aliquotaOrigem; // 7%, 12% ou 4%
    private BigDecimal valorIcmsSt; // O valor do imposto calculado

    private BigDecimal custoFinalUnitario; // (Preço + ST) / Qtd
}
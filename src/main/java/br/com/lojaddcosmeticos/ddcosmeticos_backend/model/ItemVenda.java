package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotalItem;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal descontoItem = BigDecimal.ZERO;

    // --- NOVOS CAMPOS PARA RELATÓRIO DE LUCRO ---
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal custoUnitario; // PMP no momento da venda

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal custoTotal;
}
// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/model/Venda.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade principal que representa uma transação de venda (comprovante fiscal).
 */
@Data
@Entity
@Table(name = "venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda = LocalDateTime.now();

    /**
     * O operador de caixa que registrou a venda (para auditoria).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operador_id", nullable = false)
    private Usuario operador;

    /**
     * Valor total dos itens sem descontos (Valor Bruto).
     */
    @Column(name = "valor_total", precision = 10, scale = 2, nullable = false)
    private BigDecimal valorTotal;

    /**
     * Desconto global aplicado no subtotal da venda (não inclui descontos por item).
     */
    @Column(name = "desconto_global", precision = 10, scale = 2, nullable = false)
    private BigDecimal desconto;

    /**
     * Valor final pago pelo cliente (Valor Líquido = Total Bruto - Total Descontos).
     */
    @Column(name = "valor_liquido", precision = 10, scale = 2, nullable = false)
    private BigDecimal valorLiquido;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();
}
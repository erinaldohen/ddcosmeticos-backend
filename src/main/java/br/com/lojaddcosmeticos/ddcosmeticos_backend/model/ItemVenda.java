package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@Entity
@NoArgsConstructor
@Audited
@Table(name = "item_venda")
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "custo_unitario_historico", precision = 10, scale = 4)
    private BigDecimal custoUnitarioHistorico;

    // --- NOVOS CAMPOS FISCAIS (LC 214) ---
    // Grava a alíquota aplicada no momento exato da venda para auditoria
    @Column(name = "aliquota_ibs_aplicada", precision = 10, scale = 4)
    private BigDecimal aliquotaIbsAplicada;

    @Column(name = "aliquota_cbs_aplicada", precision = 10, scale = 4)
    private BigDecimal aliquotaCbsAplicada;

    @Column(name = "valor_imposto_seletivo", precision = 10, scale = 2)
    private BigDecimal valorImpostoSeletivo = BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    // --- MÉTODOS CALCULADOS ---

    public BigDecimal getTotalItem() {
        if (precoUnitario == null || quantidade == null) return BigDecimal.ZERO;
        return precoUnitario.multiply(quantidade);
    }

    public BigDecimal getCustoTotal() {
        if (custoUnitarioHistorico == null || quantidade == null) return BigDecimal.ZERO;
        return custoUnitarioHistorico.multiply(quantidade);
    }

    public BigDecimal getLucroItem() {
        return getTotalItem().subtract(getCustoTotal());
    }

    /**
     * Calcula o total de impostos da Reforma (IBS + CBS) retidos neste item.
     */
    public BigDecimal getTotalImpostosReforma() {
        if (aliquotaIbsAplicada == null || aliquotaCbsAplicada == null) return BigDecimal.ZERO;
        BigDecimal aliquotaTotal = aliquotaIbsAplicada.add(aliquotaCbsAplicada);
        return getTotalItem().multiply(aliquotaTotal).add(valorImpostoSeletivo != null ? valorImpostoSeletivo : BigDecimal.ZERO);
    }

    /**
     * Lucro Líquido Real após impostos da Reforma e Imposto Seletivo.
     */
    public BigDecimal getLucroLiquidoItem() {
        return getLucroItem().subtract(getTotalImpostosReforma());
    }
}
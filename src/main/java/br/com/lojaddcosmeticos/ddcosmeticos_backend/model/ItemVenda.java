package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

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
    @Column(name = "aliquota_ibs_aplicada", precision = 10, scale = 4)
    private BigDecimal aliquotaIbsAplicada;

    @Column(name = "aliquota_cbs_aplicada", precision = 10, scale = 4)
    private BigDecimal aliquotaCbsAplicada;

    @Column(name = "valor_imposto_seletivo", precision = 10, scale = 2)
    private BigDecimal valorImpostoSeletivo = BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "venda_id", nullable = false)
    @JsonBackReference // Evita loop infinito no JSON
    private Venda venda;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    // --- MÉTODOS CALCULADOS (Essenciais para os DTOs) ---

    /**
     * Calcula o valor total do item (Preço * Quantidade)
     */
    public BigDecimal getTotalItem() {
        if (precoUnitario == null || quantidade == null) return BigDecimal.ZERO;
        return precoUnitario.multiply(quantidade);
    }

    /**
     * Calcula o custo total do item (Custo Histórico * Quantidade)
     * Utilizado pelo ItemVendaResponseDTO
     */
    public BigDecimal getCustoTotal() {
        if (custoUnitarioHistorico == null || quantidade == null) return BigDecimal.ZERO;
        return custoUnitarioHistorico.multiply(quantidade);
    }

    /**
     * Calcula o lucro bruto deste item
     */
    public BigDecimal getLucroItem() {
        return getTotalItem().subtract(getCustoTotal());
    }

    /**
     * Calcula o total de impostos da Reforma (IBS + CBS + IS) retidos neste item.
     */
    public BigDecimal getTotalImpostosReforma() {
        if (aliquotaIbsAplicada == null || aliquotaCbsAplicada == null) return BigDecimal.ZERO;
        BigDecimal aliquotaTotal = aliquotaIbsAplicada.add(aliquotaCbsAplicada);

        BigDecimal totalImpostos = getTotalItem().multiply(aliquotaTotal);
        if (valorImpostoSeletivo != null) {
            totalImpostos = totalImpostos.add(valorImpostoSeletivo);
        }
        return totalImpostos;
    }

    /**
     * Lucro Líquido Real após impostos da Reforma.
     */
    public BigDecimal getLucroLiquidoItem() {
        return getLucroItem().subtract(getTotalImpostosReforma());
    }
}
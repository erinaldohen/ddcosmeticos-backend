package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoInfluenciaIA;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tb_item_venda")
@Audited
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id")
    @JsonIgnore
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ToString.Include
    @Column(precision = 15, scale = 4) // PRECISÃO FISCAL OBRIGATÓRIA
    private BigDecimal quantidade = BigDecimal.ZERO;

    @ToString.Include
    @Column(precision = 15, scale = 4) // PRECISÃO FISCAL OBRIGATÓRIA
    private BigDecimal precoUnitario = BigDecimal.ZERO;

    @Column(precision = 15, scale = 4)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(precision = 15, scale = 4)
    private BigDecimal custoUnitarioHistorico = BigDecimal.ZERO;

    // =========================================================================
    // BLINDAGEM FISCAL: Fotografia exata no momento da venda (Imutável)
    // =========================================================================

    @Column(length = 20)
    private String codigoBarras; // EAN daquele exato momento

    @Column(length = 10)
    private String ncm; // NCM daquele exato momento

    @Column(length = 4)
    private String cfop;

    @Column(length = 4)
    private String csosn;

    @Column(length = 100)
    private String naturezaOperacao;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaIcms; // ICMS / Tributação

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaIbsAplicada;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaCbsAplicada;

    // =========================================================================
    // INTELIGÊNCIA ARTIFICIAL E RASTREIO DE SUGESTÕES NO PDV
    // =========================================================================

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoInfluenciaIA influenciaIA = TipoInfluenciaIA.NENHUMA;

    // =========================================================================
    // MÉTODOS DE CONVENIÊNCIA E MATEMÁTICA CORRIGIDA
    // =========================================================================

    public BigDecimal getValorTotalItem() {
        BigDecimal qtd = this.quantidade != null ? this.quantidade : BigDecimal.ZERO;
        BigDecimal preco = this.precoUnitario != null ? this.precoUnitario : BigDecimal.ZERO;
        BigDecimal desc = this.desconto != null ? this.desconto : BigDecimal.ZERO;

        // 🚨 CORREÇÃO MATEMÁTICA SEFAZ: (Preço * Qtd) - Desconto
        return preco.multiply(qtd).subtract(desc).max(BigDecimal.ZERO);
    }

    public BigDecimal getCustoTotalItem() {
        BigDecimal qtd = this.quantidade != null ? this.quantidade : BigDecimal.ZERO;
        BigDecimal custo = this.custoUnitarioHistorico != null ? this.custoUnitarioHistorico : BigDecimal.ZERO;

        return custo.multiply(qtd);
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoInfluenciaIA; // <--- NOVO IMPORT
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "tb_item_venda")
@Audited
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venda_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore // Impede o loop infinito no JSON
    private Venda venda;

    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal desconto;

    // --- CUSTOS E TRIBUTAÇÃO FUTURA ---
    private BigDecimal custoUnitarioHistorico;
    private BigDecimal aliquotaIbsAplicada;
    private BigDecimal aliquotaCbsAplicada;

    // =========================================================================
    // BLINDAGEM FISCAL: Fotografia exata da tributação no momento da venda
    // =========================================================================

    @Column(length = 4)
    private String cfop;

    @Column(length = 4)
    private String csosn;

    @Column(length = 100)
    private String naturezaOperacao;

    // =========================================================================
    // NOVO: INTELIGÊNCIA ARTIFICIAL E RASTREIO DE SUGESTÕES NO PDV
    // =========================================================================

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoInfluenciaIA influenciaIA = TipoInfluenciaIA.NENHUMA;

}
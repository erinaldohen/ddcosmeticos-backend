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

// DBA/Performance: Substituído @Data por Getter, Setter e Equals explícito
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tb_item_venda")
@Audited
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Impede loop infinito no Hash
@ToString(onlyExplicitlyIncluded = true)
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // OTIMIZAÇÃO: Não carrega a venda inteira ao listar itens
    @JoinColumn(name = "venda_id")
    @JsonIgnore // Impede o loop infinito no JSON
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY) // OTIMIZAÇÃO: Não faz JOIN com produto a menos que seja pedido
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ToString.Include
    private BigDecimal quantidade = BigDecimal.ZERO;

    @ToString.Include
    private BigDecimal precoUnitario = BigDecimal.ZERO;

    private BigDecimal desconto = BigDecimal.ZERO;

    // --- CUSTOS E TRIBUTAÇÃO FUTURA ---
    private BigDecimal custoUnitarioHistorico = BigDecimal.ZERO;
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

    // =========================================================================
    // MÉTODOS DE CONVENIÊNCIA (Para Relatórios e Comissões)
    // =========================================================================

    public BigDecimal getValorTotalItem() {
        BigDecimal qtd = this.quantidade != null ? this.quantidade : BigDecimal.ZERO;
        BigDecimal preco = this.precoUnitario != null ? this.precoUnitario : BigDecimal.ZERO;
        BigDecimal desc = this.desconto != null ? this.desconto : BigDecimal.ZERO;

        return preco.subtract(desc).multiply(qtd);
    }

    public BigDecimal getCustoTotalItem() {
        BigDecimal qtd = this.quantidade != null ? this.quantidade : BigDecimal.ZERO;
        BigDecimal custo = this.custoUnitarioHistorico != null ? this.custoUnitarioHistorico : BigDecimal.ZERO;

        return custo.multiply(qtd);
    }
}
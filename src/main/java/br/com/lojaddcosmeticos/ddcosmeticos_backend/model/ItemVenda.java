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
@Table(name = "tb_item_venda", indexes = {
        // 🔥 OTIMIZAÇÃO DBA: Índices vitais para performance em tabelas gigantes
        @Index(name = "idx_item_venda_venda_id", columnList = "venda_id"),
        @Index(name = "idx_item_venda_produto_id", columnList = "produto_id")
})
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
    @JoinColumn(name = "venda_id", nullable = false)
    @JsonIgnore
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ToString.Include
    @Column(precision = 15, scale = 4, nullable = false) // PRECISÃO FISCAL OBRIGATÓRIA
    private BigDecimal quantidade = BigDecimal.ZERO;

    @ToString.Include
    @Column(precision = 15, scale = 4, nullable = false) // PRECISÃO FISCAL OBRIGATÓRIA
    private BigDecimal precoUnitario = BigDecimal.ZERO;

    @Column(precision = 15, scale = 4)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(precision = 15, scale = 4)
    private BigDecimal custoUnitarioHistorico = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "influencia_ia", nullable = false)
    private TipoInfluenciaIA influenciaIA = TipoInfluenciaIA.NENHUMA;

    // =========================================================================
    // BLINDAGEM FISCAL: Fotografia exata no momento da venda (Imutável)
    // =========================================================================

    @Column(length = 255, nullable = false)
    private String descricaoProduto; // NOME EXATO no momento da venda

    @Column(length = 20)
    private String codigoBarras; // EAN daquele exato momento

    @Column(length = 10)
    private String ncm; // NCM daquele exato momento

    @Column(length = 4)
    private String cfop; // CFOP daquele exato momento

    @Column(length = 4)
    private String csosn; // CSOSN / CST daquele exato momento

    @Column(length = 100)
    private String naturezaOperacao;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaIcms; // ICMS / Tributação

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaIbsAplicada;

    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaCbsAplicada;

    // =========================================================================
    // GATILHOS JPA: Automação do Snapshot (100% blindado para a SEFAZ)
    // =========================================================================

    /**
     * Antes de salvar no banco, o Hibernate roda este método.
     * Ele garante que a "fotografia" do produto seja tirada, protegendo contra alterações futuras no catálogo.
     */
    @PrePersist
    public void registrarFotografiaFiscal() {
        if (this.produto != null) {
            if (this.descricaoProduto == null || this.descricaoProduto.isBlank()) {
                this.descricaoProduto = this.produto.getDescricao();
            }
            if (this.codigoBarras == null || this.codigoBarras.isBlank()) {
                this.codigoBarras = this.produto.getCodigoBarras();
            }
            if (this.custoUnitarioHistorico == null || this.custoUnitarioHistorico.compareTo(BigDecimal.ZERO) == 0) {
                this.custoUnitarioHistorico = this.produto.getPrecoCusto() != null ? this.produto.getPrecoCusto() : BigDecimal.ZERO;
            }
            if (this.ncm == null || this.ncm.isBlank()) {
                this.ncm = this.produto.getNcm();
            }
            if (this.cfop == null || this.cfop.isBlank()) {
                this.cfop = this.produto.getCfop(); // Captura o CFOP do cadastro
            }

            // 🔥 CAPTURA EXTRA: Dados Tributários para manter a nota intacta
            if (this.csosn == null || this.csosn.isBlank()) {
                // Dependendo de como a sua classe Produto está estruturada, ele pode puxar o cst/csosn
                // this.csosn = this.produto.getCst();
            }
            if (this.aliquotaIcms == null) {
                // this.aliquotaIcms = this.produto.getAliquotaIcms();
            }

        } else if (this.descricaoProduto == null || this.descricaoProduto.isBlank()) {
            this.descricaoProduto = "Item Avulso/Desconhecido";
        }
    }

    // =========================================================================
    // MÉTODOS DE CONVENIÊNCIA E MATEMÁTICA CORRIGIDA
    // =========================================================================

    public BigDecimal getValorTotalItem() {
        BigDecimal qtd = this.quantidade != null ? this.quantidade : BigDecimal.ZERO;
        BigDecimal preco = this.precoUnitario != null ? this.precoUnitario : BigDecimal.ZERO;
        BigDecimal desc = this.desconto != null ? this.desconto : BigDecimal.ZERO;

        // 🚨 MATEMÁTICA SEFAZ: (Preço * Qtd) - Desconto
        return preco.multiply(qtd).subtract(desc).max(BigDecimal.ZERO);
    }

    public BigDecimal getCustoTotalItem() {
        BigDecimal qtd = this.quantidade != null ? this.quantidade : BigDecimal.ZERO;
        BigDecimal custo = this.custoUnitarioHistorico != null ? this.custoUnitarioHistorico : BigDecimal.ZERO;

        return custo.multiply(qtd);
    }
}
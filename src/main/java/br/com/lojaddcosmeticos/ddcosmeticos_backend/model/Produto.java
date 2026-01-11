package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Getter
@Setter
@ToString
@Entity
@NoArgsConstructor
@Audited
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "produto", indexes = {
        @Index(name = "idx_produto_descricao", columnList = "descricao"),
        @Index(name = "idx_produto_ean", columnList = "codigo_barras"),
        @Index(name = "idx_produto_ativo", columnList = "ativo")
})
@SQLDelete(sql = "UPDATE produto SET ativo = false WHERE id = ?")
@SQLRestriction("ativo = true")
public class Produto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_barras", unique = true, length = 20)
    private String codigoBarras;

    @Column(nullable = false, length = 150)
    private String descricao;

    // --- CLASSIFICAÇÃO ---
    private String marca;
    private String categoria;
    private String subcategoria;
    private String unidade = "UN";

    // --- DADOS FISCAIS ---
    @Column(length = 10)
    private String ncm;
    @Column(length = 10)
    private String cest;
    @Column(length = 4)
    private String cst;
    private boolean monofasico = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "classificacao_reforma")
    private TipoTributacaoReforma classificacaoReforma = TipoTributacaoReforma.PADRAO;

    // --- FINANCEIRO ---
    @Column(name = "preco_venda", precision = 10, scale = 2, nullable = false)
    private BigDecimal precoVenda;

    @Column(name = "preco_custo", precision = 10, scale = 2)
    private BigDecimal precoCusto;

    @Column(name = "preco_medio", precision = 10, scale = 4)
    private BigDecimal precoMedioPonderado = BigDecimal.ZERO;

    // --- ESTOQUE E CONTROLE ---
    @Column(name = "estoque_fiscal", nullable = false)
    private Integer estoqueFiscal = 0;

    @Column(name = "estoque_nao_fiscal", nullable = false)
    private Integer estoqueNaoFiscal = 0;

    @Column(name = "quantidade_estoque")
    private Integer quantidadeEmEstoque = 0;

    private String urlImagem;

    @Column(nullable = false)
    private boolean ativo = true;

    // --- INTELIGÊNCIA DE REPOSIÇÃO ---
    @Column(name = "venda_media_diaria", precision = 10, scale = 3)
    private BigDecimal vendaMediaDiaria = BigDecimal.ZERO;

    private Integer diasParaReposicao = 7;

    @Column(name = "estoque_minimo")
    private Integer estoqueMinimo;

    // --- MÉTODOS DE NEGÓCIO ---

    public void atualizarSaldoTotal() {
        this.quantidadeEmEstoque = (this.estoqueFiscal != null ? this.estoqueFiscal : 0) +
                (this.estoqueNaoFiscal != null ? this.estoqueNaoFiscal : 0);
    }

    public void recalcularEstoqueMinimoSugerido() {
        if (this.vendaMediaDiaria != null && this.diasParaReposicao != null) {
            this.estoqueMinimo = this.vendaMediaDiaria.multiply(new BigDecimal(this.diasParaReposicao)).intValue();
            // Se vende algo (>0) e a conta deu 0 (ex: 0.1 * 7 = 0.7 -> int 0), sugere pelo menos 1
            if (this.estoqueMinimo == 0 && this.vendaMediaDiaria.compareTo(BigDecimal.ZERO) > 0) {
                this.estoqueMinimo = 1;
            }
        }
    }

    // --- CICLO DE VIDA ---

    @PrePersist
    @PreUpdate
    public void preSalvar() {
        if (this.descricao != null) this.descricao = this.descricao.toUpperCase().trim();
        if (this.marca != null) this.marca = this.marca.toUpperCase().trim();

        // Limpeza de caracteres especiais para manter consistência
        if (this.ncm != null) this.ncm = this.ncm.replaceAll("\\D", "");
        if (this.cest != null) this.cest = this.cest.replaceAll("\\D", "");
        if (this.codigoBarras != null) this.codigoBarras = this.codigoBarras.replaceAll("\\D", "");

        // Valores Default Seguros
        if (this.precoMedioPonderado == null) {
            this.precoMedioPonderado = this.precoCusto != null ? this.precoCusto : BigDecimal.ZERO;
        }
        if (this.estoqueFiscal == null) this.estoqueFiscal = 0;
        if (this.estoqueNaoFiscal == null) this.estoqueNaoFiscal = 0;

        atualizarSaldoTotal();
    }

    // --- EQUALS & HASHCODE (Melhor prática JPA: Usar apenas ID) ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Produto produto = (Produto) o;
        return id != null && Objects.equals(id, produto.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
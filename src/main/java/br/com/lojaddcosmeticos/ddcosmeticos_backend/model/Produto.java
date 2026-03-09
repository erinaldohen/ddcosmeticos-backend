package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Audited
@Table(name = "produto")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // --- DADOS BÁSICOS ---
    @Column(columnDefinition = "TEXT")
    @ToString.Include
    private String descricao;

    @Column(name = "codigo_barras", unique = true, length = 50)
    @ToString.Include
    private String codigoBarras;

    @Column(unique = true, length = 50)
    private String sku;

    @Column(length = 100)
    private String marca;

    @Column(length = 100)
    private String categoria; // Utilizado pelo Dashboard para o Top Categorias

    @Column(length = 100)
    private String subcategoria;

    @Column(length = 10)
    private String unidade;

    @Column(columnDefinition = "TEXT")
    private String urlImagem;

    // --- CONTROLE DE VALIDADE & LOTE ---
    @Column(length = 100)
    private String lote;

    private LocalDate validade;

    // --- DADOS FISCAIS ---
    @Column(length = 20)
    private String ncm;

    @Column(length = 20)
    private String cest;

    @Column(length = 10)
    private String cfop;

    @Column(length = 10)
    private String cst;

    @Column(length = 50)
    private String origem;

    private Boolean isMonofasico;
    private Boolean isImpostoSeletivo;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private TipoTributacaoReforma classificacaoReforma;

    // --- DADOS FINANCEIROS ---
    @Column(precision = 15, scale = 4)
    private BigDecimal precoVenda;

    @Column(precision = 15, scale = 4)
    private BigDecimal precoCusto;

    @Column(precision = 15, scale = 4)
    private BigDecimal precoMedioPonderado;

    // --- ESTOQUE ---
    private Integer quantidadeEmEstoque;
    private Integer estoqueFiscal;
    private Integer estoqueNaoFiscal;
    private Integer estoqueMinimo;
    private Integer diasParaReposicao; // Lead time para inteligência de compra

    @Column(precision = 10, scale = 4)
    private BigDecimal vendaMediaDiaria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    private boolean ativo = true;

    @Column(updatable = false)
    private LocalDateTime dataCadastro;
    private LocalDateTime dataAtualizacao;

    private LocalDate dataUltimaVenda;
    // --- EVENTOS DO CICLO DE VIDA ---

    @PrePersist
    public void prePersist() {
        this.dataCadastro = LocalDateTime.now();
        if (this.isMonofasico == null) this.isMonofasico = false;
        if (this.isImpostoSeletivo == null) this.isImpostoSeletivo = false;
        if (this.quantidadeEmEstoque == null) this.quantidadeEmEstoque = 0;
        if (this.estoqueFiscal == null) this.estoqueFiscal = 0;
        if (this.estoqueNaoFiscal == null) this.estoqueNaoFiscal = 0;
        if (this.vendaMediaDiaria == null) this.vendaMediaDiaria = BigDecimal.ZERO;
        if (this.diasParaReposicao == null) this.diasParaReposicao = 7;
        if (this.sku == null && this.codigoBarras != null) this.sku = this.codigoBarras;
    }

    @PreUpdate
    public void preUpdate() {
        this.dataAtualizacao = LocalDateTime.now();
        if (this.estoqueFiscal == null) this.estoqueFiscal = 0;
        if (this.estoqueNaoFiscal == null) this.estoqueNaoFiscal = 0;
        this.atualizarSaldoTotal();
    }

    // --- MÉTODOS AUXILIARES ---

    public Boolean isMonofasico() {
        return isMonofasico != null && isMonofasico;
    }

    public boolean isVencido() {
        if (this.validade == null) return false;
        return LocalDate.now().isAfter(this.validade);
    }

    public void atualizarSaldoTotal() {
        this.quantidadeEmEstoque = (this.estoqueFiscal != null ? this.estoqueFiscal : 0)
                + (this.estoqueNaoFiscal != null ? this.estoqueNaoFiscal : 0);
    }

    public void recalcularEstoqueMinimoSugerido() {
        if (this.vendaMediaDiaria != null && this.vendaMediaDiaria.compareTo(BigDecimal.ZERO) > 0) {
            int diasLeadTime = (this.diasParaReposicao != null ? this.diasParaReposicao : 7);
            int diasTotais = diasLeadTime + 2;

            this.estoqueMinimo = this.vendaMediaDiaria
                    .multiply(new BigDecimal(diasTotais))
                    .setScale(0, RoundingMode.CEILING)
                    .intValue();
        }
    }
}
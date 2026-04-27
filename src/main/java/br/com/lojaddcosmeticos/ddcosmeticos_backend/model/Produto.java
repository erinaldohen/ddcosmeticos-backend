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
@Table(name = "produto", indexes = {
        // 🔥 OTIMIZAÇÃO DBA: Índices vitais para PDV rápido e relatórios de BI instantâneos
        @Index(name = "idx_produto_cod_barras", columnList = "codigo_barras"),
        @Index(name = "idx_produto_sku", columnList = "sku"),
        @Index(name = "idx_produto_fornecedor", columnList = "fornecedor_id"),
        @Index(name = "idx_produto_categoria", columnList = "categoria"),
        @Index(name = "idx_produto_marca", columnList = "marca"),
        @Index(name = "idx_produto_ativo", columnList = "ativo")
})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // --- DADOS BÁSICOS ---
    @Column(columnDefinition = "TEXT", nullable = false)
    @ToString.Include
    private String descricao;

    @Column(name = "hash_imagem", length = 64)
    private String hashImagem;

    @Column(name = "alerta_gondola")
    private Boolean alertaGondola = false;

    @Column(name = "revisao_imagem_pendente", columnDefinition = "boolean default false")
    private Boolean revisaoImagemPendente = false;

    @Column(name = "codigo_barras", unique = true, length = 50)
    @ToString.Include
    private String codigoBarras;

    @Column(unique = true, length = 50)
    private String sku;

    @Column(length = 100)
    private String marca;

    @Column(name = "revisao_pendente")
    private Boolean revisaoPendente = false;

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

    private Boolean isMonofasico = false;
    private Boolean isImpostoSeletivo = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private TipoTributacaoReforma classificacaoReforma;

    // --- DADOS FINANCEIROS ---
    // 🛡️ Segurança: Inicializado em ZERO para evitar NullPointerException em relatórios
    @Column(precision = 15, scale = 4)
    private BigDecimal precoVenda = BigDecimal.ZERO;

    @Column(precision = 15, scale = 4)
    private BigDecimal precoCusto = BigDecimal.ZERO;

    @Column(precision = 15, scale = 4)
    private BigDecimal precoMedioPonderado = BigDecimal.ZERO;

    // --- ESTOQUE ---
    private Integer quantidadeEmEstoque = 0;
    private Integer estoqueFiscal = 0;
    private Integer estoqueNaoFiscal = 0;
    private Integer estoqueMinimo = 0;
    private Integer diasParaReposicao = 7; // Lead time para inteligência de compra

    @Column(precision = 10, scale = 4)
    private BigDecimal vendaMediaDiaria = BigDecimal.ZERO;

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

        // Garante que não guardamos nulo nos valores (Cão de guarda financeiro)
        if (this.precoCusto == null) this.precoCusto = BigDecimal.ZERO;
        if (this.precoVenda == null) this.precoVenda = BigDecimal.ZERO;
        if (this.precoMedioPonderado == null) this.precoMedioPonderado = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.dataAtualizacao = LocalDateTime.now();
        if (this.estoqueFiscal == null) this.estoqueFiscal = 0;
        if (this.estoqueNaoFiscal == null) this.estoqueNaoFiscal = 0;
        this.atualizarSaldoTotal();
    }

    // --- MÉTODOS AUXILIARES ---

    // Renomeado ligeiramente para não dar conflito com a geração de Getters do Lombok
    public Boolean verificarSeMonofasico() {
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
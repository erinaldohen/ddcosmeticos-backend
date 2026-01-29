package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
@Entity
@Audited
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- DADOS BÁSICOS ---
    private String descricao;
    @Column(name = "codigo_barras", unique = true)
    private String codigoBarras;

    private String marca;
    private String categoria;
    private String subcategoria;
    private String unidade;
    private String urlImagem;

    // --- DADOS FISCAIS ---
    private String ncm;
    private String cest;
    private String cfop;
    private String cst;
    private String origem;

    private Boolean isMonofasico;
    private Boolean isImpostoSeletivo;

    @Enumerated(EnumType.STRING)
    private TipoTributacaoReforma classificacaoReforma;

    // --- DADOS FINANCEIROS ---
    private BigDecimal precoVenda;
    private BigDecimal precoCusto;
    private BigDecimal precoMedioPonderado;

    // --- ESTOQUE ---
    private Integer quantidadeEmEstoque; // Total
    private Integer estoqueFiscal;       // Nota Fiscal
    private Integer estoqueNaoFiscal;    // Sem Nota

    private Integer estoqueMinimo;
    private Integer diasParaReposicao; // Lead time (tempo de entrega do fornecedor)

    // NOVO: Média de vendas diária (calculada por job noturno) para inteligência de estoque
    @Column(precision = 10, scale = 4)
    private BigDecimal vendaMediaDiaria;

    @ManyToOne
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    private boolean ativo = true;

    @Column(updatable = false)
    private LocalDateTime dataCadastro;
    private LocalDateTime dataAtualizacao;

    @PrePersist
    public void prePersist() {
        this.dataCadastro = LocalDateTime.now();
        if (this.isMonofasico == null) this.isMonofasico = false;
        if (this.isImpostoSeletivo == null) this.isImpostoSeletivo = false;

        // Inicialização segura de estoques
        if (this.quantidadeEmEstoque == null) this.quantidadeEmEstoque = 0;
        if (this.estoqueFiscal == null) this.estoqueFiscal = 0;
        if (this.estoqueNaoFiscal == null) this.estoqueNaoFiscal = 0;

        // Inicialização de inteligência
        if (this.vendaMediaDiaria == null) this.vendaMediaDiaria = BigDecimal.ZERO;
        if (this.diasParaReposicao == null) this.diasParaReposicao = 7; // Padrão 7 dias
    }

    @PreUpdate
    public void preUpdate() {
        this.dataAtualizacao = LocalDateTime.now();
        // Garante integridade se um campo for nulo na atualização
        if (this.estoqueFiscal == null) this.estoqueFiscal = 0;
        if (this.estoqueNaoFiscal == null) this.estoqueNaoFiscal = 0;
    }

    public Boolean isMonofasico() {
        return isMonofasico != null && isMonofasico;
    }

    public Boolean isImpostoSeletivo() {
        return isImpostoSeletivo != null && isImpostoSeletivo;
    }

    /**
     * Atualiza o saldo total somando Fiscal + Não Fiscal.
     * Chamado pelo EstoqueIntelligenceService.
     */
    public void atualizarSaldoTotal() {
        this.quantidadeEmEstoque = (this.estoqueFiscal != null ? this.estoqueFiscal : 0)
                + (this.estoqueNaoFiscal != null ? this.estoqueNaoFiscal : 0);
    }

    /**
     * Recalcula o estoque mínimo ideal baseado na média de vendas.
     * Fórmula: Média Diária * (Dias Reposição + Margem Segurança Fixa de 2 dias)
     */
    public void recalcularEstoqueMinimoSugerido() {
        if (this.vendaMediaDiaria != null && this.vendaMediaDiaria.compareTo(BigDecimal.ZERO) > 0) {
            int diasLeadTime = (this.diasParaReposicao != null ? this.diasParaReposicao : 7);
            int diasTotais = diasLeadTime + 2; // +2 dias de margem de segurança interna

            this.estoqueMinimo = this.vendaMediaDiaria
                    .multiply(new BigDecimal(diasTotais))
                    .setScale(0, RoundingMode.CEILING) // Arredonda pra cima para não faltar
                    .intValue();
        }
    }
}
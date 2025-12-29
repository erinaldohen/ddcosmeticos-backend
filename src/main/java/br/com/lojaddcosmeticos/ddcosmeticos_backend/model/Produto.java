package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
@Table(name = "produto")
@SQLDelete(sql = "UPDATE produto SET ativo = false WHERE id = ?")
@SQLRestriction("ativo = true")
public class Produto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_barras", unique = true, length = 20)
    private String codigoBarras;

    @Column(nullable = false, length = 500)
    private String descricao;

    @Column(name = "preco_custo", precision = 10, scale = 2)
    private BigDecimal precoCusto;

    @Column(name = "preco_medio", precision = 10, scale = 4)
    private BigDecimal precoMedioPonderado;

    @Column(name = "preco_venda", precision = 10, scale = 2)
    private BigDecimal precoVenda;

    @Column(length = 10)
    private String unidade = "UN";

    @Column(name = "monofasico")
    private boolean monofasico = false;

    // --- DADOS FISCAIS ---

    @Column(length = 20)
    private String ncm;

    @Column(length = 20)
    private String cest;

    @Column(length = 10)
    private String cst;

    // --- NOVA LEI COMPLEMENTAR 214/2025 ---
    @Enumerated(EnumType.STRING)
    @Column(name = "classificacao_reforma", length = 30)
    private TipoTributacaoReforma classificacaoReforma = TipoTributacaoReforma.PADRAO;

    // --- ESTOQUES ---

    @Column(name = "estoque_fiscal", nullable = false)
    private Integer estoqueFiscal = 0;

    @Column(name = "estoque_nao_fiscal", nullable = false)
    private Integer estoqueNaoFiscal = 0;

    @Column(name = "quantidade_estoque")
    private Integer quantidadeEmEstoque = 0;

    public void atualizarSaldoTotal() {
        this.quantidadeEmEstoque = (this.estoqueFiscal != null ? this.estoqueFiscal : 0) +
                (this.estoqueNaoFiscal != null ? this.estoqueNaoFiscal : 0);
    }

    private String urlImagem;

    @Column(nullable = false)
    private boolean ativo = true;

    // --- INTELIGÊNCIA ---

    @Column(name = "venda_media_diaria", precision = 10, scale = 3)
    private BigDecimal vendaMediaDiaria = BigDecimal.ZERO;

    private Integer diasParaReposicao = 7;

    @Column(name = "estoque_minimo")
    private Integer estoqueMinimo;

    public void recalcularEstoqueMinimoSugerido() {
        if (this.vendaMediaDiaria != null && this.diasParaReposicao != null) {
            this.estoqueMinimo = this.vendaMediaDiaria.multiply(new BigDecimal(this.diasParaReposicao)).intValue();
            if (this.estoqueMinimo == 0 && this.vendaMediaDiaria.compareTo(BigDecimal.ZERO) > 0) {
                this.estoqueMinimo = 1;
            }
        }
    }

    @Transient
    public boolean isPossuiNfEntrada() {
        return this.estoqueFiscal != null && this.estoqueFiscal > 0;
    }

    @Transient
    public void setPossuiNfEntrada(boolean valor) {}
}
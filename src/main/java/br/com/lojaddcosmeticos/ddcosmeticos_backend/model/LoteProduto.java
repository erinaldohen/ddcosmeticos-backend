package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited; // Recomendado para rastreabilidade

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Audited
@NoArgsConstructor
@Table(name = "lote_produto")
public class LoteProduto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "numero_lote", nullable = false)
    private String numeroLote;

    @Column(name = "data_validade", nullable = false)
    private LocalDate dataValidade;

    // --- CORREÇÃO: Campos adicionados para suportar o Service ---
    @Column(name = "data_fabricacao")
    private LocalDate dataFabricacao;

    @Column(name = "quantidade_inicial", precision = 10, scale = 4)
    private BigDecimal quantidadeInicial;

    @Column(name = "preco_custo", precision = 10, scale = 2)
    private BigDecimal precoCusto;
    // -----------------------------------------------------------

    @Column(name = "quantidade_atual", nullable = false, precision = 10, scale = 4)
    private BigDecimal quantidadeAtual = BigDecimal.ZERO;

    @Column(name = "ativo")
    private boolean ativo = true;

    // Construtor auxiliar atualizado
    public LoteProduto(Produto produto, String numeroLote, LocalDate dataValidade, BigDecimal quantidade) {
        this.produto = produto;
        this.numeroLote = numeroLote;
        this.dataValidade = dataValidade;
        this.quantidadeInicial = quantidade;
        this.quantidadeAtual = quantidade;
        this.ativo = true;
    }
}
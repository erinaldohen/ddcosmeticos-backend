package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
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

    @Column(name = "quantidade_atual", nullable = false)
    private BigDecimal quantidadeAtual = BigDecimal.ZERO;

    @Column(name = "ativo")
    private boolean ativo = true;

    // Construtor auxiliar
    public LoteProduto(Produto produto, String numeroLote, LocalDate dataValidade, BigDecimal quantidade) {
        this.produto = produto;
        this.numeroLote = numeroLote;
        this.dataValidade = dataValidade;
        this.quantidadeAtual = quantidade;
        this.ativo = true;
    }
}
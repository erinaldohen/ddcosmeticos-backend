package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "movimento_estoque")
public class MovimentoEstoque implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "tipo_movimento", nullable = false) // ENTRADA ou SAIDA
    private String tipoMovimento;

    @Column(name = "quantidade_movimentada", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantidadeMovimentada;

    @Column(name = "data_movimento", nullable = false)
    private LocalDateTime dataMovimento = LocalDateTime.now();

    // Custo no momento da movimentação (Snapshot)
    @Column(name = "custo_movimentado", precision = 10, scale = 4)
    private BigDecimal custoMovimentado;

    @Column(name = "id_referencia") // ID da Venda ou Nota
    private Long idReferencia;

    @ManyToOne
    @JoinColumn(name = "fornecedor_id") // Nova coluna
    private Fornecedor fornecedor;
}
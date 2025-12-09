// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/model/MovimentoEstoque.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade responsável por auditar todas as alterações no estoque de um produto.
 * Cada registro representa uma transação de entrada ou saída.
 */
@Data
@Entity
@Table(name = "movimento_estoque")
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Produto afetado pelo movimento de estoque.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    /**
     * Quantidade movimentada (positiva para entrada, negativa para saída).
     */
    @Column(name = "quantidade_movimentada", precision = 10, scale = 3, nullable = false)
    private BigDecimal quantidadeMovimentada;

    /**
     * Data e hora exata do movimento.
     */
    @Column(name = "data_movimento", nullable = false)
    private LocalDateTime dataMovimento = LocalDateTime.now();

    /**
     * Tipo de movimento (Ex: VENDA, ENTRADA_NF, AJUSTE_SAIDA, etc.).
     */
    @Column(name = "tipo_movimento", length = 50, nullable = false)
    private String tipoMovimento;

    /**
     * ID de referência da transação original (Ex: ID da Venda, ID da NF-e de entrada).
     */
    @Column(name = "id_referencia")
    private Long idReferencia;
}
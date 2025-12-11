// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/model/MovimentoEstoque.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade de auditoria para rastrear todas as movimentações de estoque.
 */
@Data
@Entity
@Table(name = "movimento_estoque")
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "data_movimento", nullable = false)
    private LocalDateTime dataMovimento = LocalDateTime.now();

    /**
     * Quantidade que entrou (positivo) ou saiu (negativo).
     */
    @Column(name = "quantidade_movimentada", precision = 10, scale = 3, nullable = false)
    private BigDecimal quantidadeMovimentada;

    /**
     * NOVO CAMPO: Custo total (para aquela quantidade).
     * Positivo para entrada, negativo para saída.
     */
    @Column(name = "custo_movimentado", precision = 10, scale = 4, nullable = false)
    private BigDecimal custoMovimentado;

    /**
     * Tipo de movimento (Ex: 'ENTRADA_NF', 'VENDA_PDV', 'AJUSTE').
     */
    @Column(name = "tipo_movimento", length = 50, nullable = false)
    private String tipoMovimento;

    /**
     * ID da entidade que gerou o movimento (Ex: ID da Venda ou ID da NF).
     */
    @Column(name = "id_referencia")
    private Long idReferencia;

    public MovimentoEstoque() {
    }

    public MovimentoEstoque(Produto produto, LocalDateTime dataMovimento, BigDecimal quantidadeMovimentada, String tipoMovimento, BigDecimal custoMovimentado) {
        this.produto = produto;
        this.dataMovimento = dataMovimento;
        this.quantidadeMovimentada = quantidadeMovimentada;
        this.tipoMovimento = tipoMovimento;
        this.custoMovimentado = custoMovimentado;
    }
}
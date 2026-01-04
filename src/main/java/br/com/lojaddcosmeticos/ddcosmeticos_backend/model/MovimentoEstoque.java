package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@Table(name = "movimento_estoque")
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime dataMovimento = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "tipo_movimento")
    private TipoMovimentoEstoque tipoMovimentoEstoque;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "motivo_movimentacao")
    private MotivoMovimentacaoDeEstoque motivoMovimentacaoDeEstoque;

    @Column(name = "quantidade_movimentada", precision = 10, scale = 4)
    private BigDecimal quantidadeMovimentada;

    @Column(name = "custo_movimentado", precision = 10, scale = 4)
    private BigDecimal custoMovimentado;

    @Column(name = "documento_referencia")
    private String documentoReferencia;

    @Column(name = "saldo_anterior")
    private Integer saldoAnterior;

    @Column(name = "saldo_atual")
    private Integer saldoAtual;

    @Column(name = "movimentacao_fiscal")
    private boolean movimentacaoFiscal;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
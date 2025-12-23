package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
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

    // CORREÇÃO: Usando Enum para evitar erros de digitação ("Entrada" vs "ENTRADA")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimento", nullable = false)
    private TipoMovimentoEstoque tipoMovimentoEstoque;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_movimento")
    private MotivoMovimentacaoDeEstoque motivoMovimentacaoDeEstoque;

    @Column(name = "quantidade_movimentada", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantidadeMovimentada;

    @Column(name = "data_movimento", nullable = false)
    private LocalDateTime dataMovimento = LocalDateTime.now();

    @Column(name = "custo_movimentado", precision = 10, scale = 4)
    private BigDecimal custoMovimentado;

    @Column(name = "id_referencia")
    private Long idReferencia;

    @ManyToOne
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    // AUDITORIA: Adicionado como nullable=true para não quebrar dados antigos.
    // Em produção nova, passaria a ser false.
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
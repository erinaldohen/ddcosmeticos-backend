package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusMovimentoEstoque;
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

    // --- CORREÇÃO 1: Mudança de String para Enum ---
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimento", nullable = false)
    private StatusMovimentoEstoque statusMovimentoEstoque; // Agora aceita o Enum ENTRADA/SAIDA

    // --- CORREÇÃO 2: Novo campo de Motivo (Sobra, Perda, Venda...) ---
    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_movimento", length = 30)
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

    // --- CORREÇÃO 3: Campo de Auditoria (Quem fez?) ---
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
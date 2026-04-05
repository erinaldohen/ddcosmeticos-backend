package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusTitulo;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "tb_titulo_receber")
public class TituloReceber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // Se quiser amarrar com a Venda (Opcional, mas recomendado)
    @Column(name = "venda_id")
    private Long vendaId;

    private String descricao;

    private LocalDate dataCompra;
    private LocalDate dataVencimento;
    private LocalDate dataPagamento;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorPago = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal saldoDevedor;

    @Enumerated(EnumType.STRING)
    private StatusTitulo status = StatusTitulo.PENDENTE;
}
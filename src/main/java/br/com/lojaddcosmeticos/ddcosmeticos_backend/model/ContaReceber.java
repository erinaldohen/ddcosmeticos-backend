package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

// DBA/Performance: Substituído @Data para evitar N+1 Queries e loops infinitos
@Entity
@Table(name = "tb_contas_receber")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class ContaReceber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // OTIMIZAÇÃO: Lazy Loading impede de puxar a venda inteira apenas para ver o valor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id")
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ToString.Include
    @Column(precision = 15, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @ToString.Include
    @Column(precision = 15, scale = 2)
    private BigDecimal valorPago = BigDecimal.ZERO;

    private LocalDate dataVencimento;
    private LocalDate dataEmissao;
    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private StatusConta status = StatusConta.PENDENTE;

    @PrePersist
    public void prePersist() {
        if (this.valorPago == null) this.valorPago = BigDecimal.ZERO;
        if (this.valorTotal == null) this.valorTotal = BigDecimal.ZERO;
        if (this.dataEmissao == null) this.dataEmissao = LocalDate.now();
        if (this.status == null) this.status = StatusConta.PENDENTE;
    }

    // =========================================================================
    // MÉTODOS DE CONVENIÊNCIA (Para o Motor do Dashboard)
    // =========================================================================

    /**
     * Verifica automaticamente se a conta está paga, quer pelo status, quer pelo valor pago.
     * Necessário para o Dashboard calcular as receitas pendentes.
     */
    public boolean isPago() {
        return this.status == StatusConta.PAGO ||
                (this.valorTotal != null && this.valorPago != null && this.valorPago.compareTo(this.valorTotal) >= 0);
    }

    /**
     * Retorna o Saldo Devedor real (Valor Total - Valor Pago).
     * O Dashboard usa isto para saber exatamente quanto dinheiro vai entrar nos próximos 7 dias.
     */
    public BigDecimal getValor() {
        BigDecimal total = this.valorTotal != null ? this.valorTotal : BigDecimal.ZERO;
        BigDecimal pago = this.valorPago != null ? this.valorPago : BigDecimal.ZERO;

        BigDecimal saldoDevedor = total.subtract(pago);

        // Retorna o saldo devedor apenas se for maior que zero
        return saldoDevedor.compareTo(BigDecimal.ZERO) > 0 ? saldoDevedor : BigDecimal.ZERO;
    }
}
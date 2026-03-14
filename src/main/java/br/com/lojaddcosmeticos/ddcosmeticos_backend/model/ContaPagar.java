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

// DBA/Performance: Substituído @Data pelas anotações explícitas para evitar N+1 Queries
@Entity
@Table(name = "tb_contas_pagar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class ContaPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @ToString.Include
    @Column(length = 255)
    private String descricao;

    // OTIMIZAÇÃO: Lazy Loading impede de puxar o cadastro do Fornecedor inteiro sem necessidade
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

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
     * Verifica automaticamente se a conta está paga.
     * O Dashboard usa isto para a projeção de Fluxo Futuro (7 Dias).
     */
    public boolean isPago() {
        return this.status == StatusConta.PAGO ||
                (this.valorTotal != null && this.valorPago != null && this.valorPago.compareTo(this.valorTotal) >= 0);
    }

    /**
     * Retorna o Saldo Devedor real (Valor Total - Valor Pago).
     * Ideal para saber quanto dinheiro realmente vai sair do caixa para quitar faturas parciais.
     */
    public BigDecimal getValorPendente() {
        BigDecimal total = this.valorTotal != null ? this.valorTotal : BigDecimal.ZERO;
        BigDecimal pago = this.valorPago != null ? this.valorPago : BigDecimal.ZERO;

        BigDecimal saldoDevedor = total.subtract(pago);
        return saldoDevedor.compareTo(BigDecimal.ZERO) > 0 ? saldoDevedor : BigDecimal.ZERO;
    }
}
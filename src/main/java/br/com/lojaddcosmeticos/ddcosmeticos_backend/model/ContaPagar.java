package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tb_contas_pagar")
@Data // <--- Importante: Gera os Getters e Setters (inclusive setValorPago)
@NoArgsConstructor
@AllArgsConstructor
public class ContaPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;

    @ManyToOne
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorTotal;

    // --- CAMPO QUE ESTAVA FALTANDO OU SEM SETTER ---
    @Column(precision = 10, scale = 2)
    private BigDecimal valorPago;

    private LocalDate dataVencimento;
    private LocalDate dataEmissao;
    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    private StatusConta status;

    // Método auxiliar para evitar nulos no banco
    @PrePersist
    public void prePersist() {
        if (this.valorPago == null) {
            this.valorPago = BigDecimal.ZERO;
        }
    }
}
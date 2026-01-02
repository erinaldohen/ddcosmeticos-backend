package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Audited
@Table(name = "conta_pagar")
public class ContaPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;
    private String categoria;

    @ManyToOne // Essencial para a linha 142 do seu Service
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Column(name = "valor_total", precision = 10, scale = 2)
    private BigDecimal valorTotal;

    private LocalDate dataEmissao;
    private LocalDate dataVencimento;
    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    private StatusConta status;
}
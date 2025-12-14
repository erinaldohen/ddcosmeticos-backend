package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
public class ContaPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao; // Ex: "Compra de Mercadoria - NF 123"

    @ManyToOne
    private Fornecedor fornecedor;

    private BigDecimal valorTotal; // Valor do Boleto

    private LocalDate dataEmissao;
    private LocalDate dataVencimento;

    private LocalDate dataPagamento; // Null se não pago

    @Enumerated(EnumType.STRING)
    private StatusConta status; // PENDENTE, PAGO, ATRASADO

    // Vinculo opcional com a Entrada de Estoque (Rastreabilidade)
    // Se quiser saber: "Essa conta veio de qual entrada?"
    private Long idEntradaEstoqueRef;

    public enum StatusConta {
        PENDENTE, PAGO, ATRASADO, CANCELADO
    }
}
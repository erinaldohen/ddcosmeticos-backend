package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
public class ContaReceber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao; // Ex: "Venda #100 - Parc 1/3"

    // Dados do Cliente (Opcional, mas bom ter)
    private String nomeCliente;

    private BigDecimal valorTotal;   // Valor Bruto
    private BigDecimal valorLiquido; // Valor que cai na conta (Bruto - Taxa)

    private LocalDate dataEmissao;
    private LocalDate dataVencimento; // Quando a operadora PROMETEU pagar (Amanhã)
    private LocalDate dataRecebimento; // Quando confirmou que caiu (Conciliação)

    @Enumerated(EnumType.STRING)
    private StatusConta status; // PENDENTE, RECEBIDO

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    private Long idVendaRef; // Link com a Venda

    public enum StatusConta {
        PENDENTE, RECEBIDO, CANCELADO
    }
}
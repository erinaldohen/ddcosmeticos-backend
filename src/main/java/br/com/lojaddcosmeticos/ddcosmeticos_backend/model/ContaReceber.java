package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta; // Importe seu Enum aqui
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "conta_receber")
public class ContaReceber implements Serializable { // <--- FALTAVA ISSO
    private static final long serialVersionUID = 1L; // <--- FALTAVA ISSO

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;
    private String nomeCliente;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorLiquido;

    private LocalDate dataEmissao;
    private LocalDate dataVencimento;
    private LocalDate dataRecebimento;

    @Enumerated(EnumType.STRING)
    private StatusConta status;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    private Long idVendaRef;
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta; // <--- ESTA LINHA É OBRIGATÓRIA
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "conta_pagar")
public class ContaPagar implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;

    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    private StatusConta status; // O compilador agora sabe que este StatusConta vem do import acima

    @ManyToOne
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Column(name = "id_entrada_estoque_ref")
    private Long idEntradaEstoque;
}
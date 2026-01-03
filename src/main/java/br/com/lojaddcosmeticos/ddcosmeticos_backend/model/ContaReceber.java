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
@Table(name = "conta_receber")
public class ContaReceber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_venda_ref")
    private Long idVendaRef; // Referência à venda no PDV

    // --- CORREÇÃO: Adicionado relacionamento com Cliente ---
    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "valor_total", precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "valor_liquido", precision = 10, scale = 2)
    private BigDecimal valorLiquido; // Valor após taxas

    // --- CORREÇÃO: Campos de Baixa/Pagamento ---
    @Column(name = "valor_pago", precision = 10, scale = 2)
    private BigDecimal valorPago;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Column(name = "forma_pagamento")
    private String formaPagamento;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(length = 255)
    private String historico; // --- CORREÇÃO: Campo adicionado

    @Enumerated(EnumType.STRING)
    private StatusConta status;
}
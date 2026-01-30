package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import com.fasterxml.jackson.annotation.JsonIgnore; // <--- IMPORTANTE
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "tb_pagamento_venda")
@Audited
public class PagamentoVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venda_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore // <--- ADICIONE ISSO: Impede o loop infinito no JSON
    private Venda venda;

    @Enumerated(EnumType.STRING)
    private FormaDePagamento formaPagamento;

    private BigDecimal valor;
    private Integer parcelas;
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tb_pagamento_venda")
@Audited
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class PagamentoVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // OTIMIZAÇÃO MANTIDA
    @JoinColumn(name = "venda_id")
    @JsonIgnore // BLINDAGEM CONTRA LOOP INFINITO MANTIDA
    private Venda venda;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @ToString.Include
    private FormaDePagamento formaPagamento;

    @ToString.Include
    @Column(precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    @ToString.Include
    private Integer parcelas = 1;

    // =========================================================================
    // BLINDAGEM FISCAL PARA TEF E PIX OFICIAL (EXIGÊNCIA SEFAZ)
    // =========================================================================

    @Column(length = 100)
    private String codigoAutorizacao; // cAut: Código de autorização da maquineta ou End-to-End do PIX

    @Column(length = 14)
    private String cnpjCredenciadora; // CNPJ da Stone, Rede, PagSeguro, Cielo, etc.

    @Column(length = 20)
    private String bandeiraCartao; // tBand SEFAZ: VISA, MASTERCARD, ELO, etc.
}
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

// DBA/Performance: Substituído @Data por Getter, Setter e Equals explícito para evitar Loop Infinito
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

    @ManyToOne(fetch = FetchType.LAZY) // OTIMIZAÇÃO: Não carrega a venda inteira na memória à toa
    @JoinColumn(name = "venda_id")
    @JsonIgnore // Impede o loop infinito na serialização JSON do Spring
    private Venda venda;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @ToString.Include
    private FormaDePagamento formaPagamento;

    @ToString.Include
    @Column(precision = 15, scale = 2) // BLINDAGEM FISCAL: Garante que os cêntimos não são perdidos
    private BigDecimal valor = BigDecimal.ZERO;

    @ToString.Include
    private Integer parcelas = 1;
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "configuracao_loja")
public class ConfiguracaoLoja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ex: 10% de custo fixo (Aluguel, Luz, Salários) sobre o faturamento
    @Column(name = "percentual_custo_fixo")
    private BigDecimal percentualCustoFixo = new BigDecimal("10.00");

    // Ex: 6% de imposto (Simples Nacional)
    @Column(name = "percentual_impostos_venda")
    private BigDecimal percentualImpostosVenda = new BigDecimal("6.00");

    // Ex: Quero ganhar 20% líquido
    @Column(name = "margem_lucro_alvo")
    private BigDecimal margemLucroAlvo = new BigDecimal("20.00");
}
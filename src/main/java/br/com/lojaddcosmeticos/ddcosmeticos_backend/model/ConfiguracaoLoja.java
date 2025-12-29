package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
@Table(name = "configuracao_loja")
public class ConfiguracaoLoja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Dados da Empresa
    private String nomeFantasia;
    private String cnpj;
    private String endereco;

    // --- PARÂMETROS DE PRECIFICAÇÃO (Campos que faltavam) ---

    @Column(name = "margem_lucro_alvo")
    private BigDecimal margemLucroAlvo = new BigDecimal("30.00"); // 30% Padrão

    @Column(name = "perc_impostos_venda")
    private BigDecimal percentualImpostosVenda = new BigDecimal("4.00"); // 4% (Simples Nacional aprox.)

    @Column(name = "perc_custo_fixo")
    private BigDecimal percentualCustoFixo = new BigDecimal("10.00"); // 10% (Aluguel, Luz, etc)

    // --- PARÂMETROS DE SEGURANÇA ---

    @Column(name = "max_desconto_caixa")
    private BigDecimal percentualMaximoDescontoCaixa = new BigDecimal("5.00");

    @Column(name = "max_desconto_gerente")
    private BigDecimal percentualMaximoDescontoGerente = new BigDecimal("20.00");
}
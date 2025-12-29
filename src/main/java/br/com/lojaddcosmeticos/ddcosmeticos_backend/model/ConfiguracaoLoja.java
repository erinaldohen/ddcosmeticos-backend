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

    // Nome da loja para cabeçalho de cupom/relatórios
    private String nomeFantasia;

    private String cnpj;

    private String endereco;

    // --- PARÂMETROS DE PRECIFICAÇÃO ---

    // Margem mínima desejada (ex: 30%) para alertas
    private BigDecimal margemLucroAlvo;

    // --- PARÂMETROS DE SEGURANÇA (NOVO) ---

    // Desconto máximo permitido para perfil CAIXA (ex: 5%)
    @Column(name = "max_desconto_caixa")
    private BigDecimal percentualMaximoDescontoCaixa = new BigDecimal("5.00");

    // Desconto máximo permitido para perfil GERENTE (ex: 20%)
    @Column(name = "max_desconto_gerente")
    private BigDecimal percentualMaximoDescontoGerente = new BigDecimal("20.00");
}
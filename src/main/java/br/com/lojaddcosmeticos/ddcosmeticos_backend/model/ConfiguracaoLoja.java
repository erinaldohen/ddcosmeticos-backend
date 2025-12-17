package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Entity
public class ConfiguracaoLoja implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ex: 19% (Média de aluguel, luz, água, folha sobre o faturamento)
    private BigDecimal percentualCustoFixo;

    // Ex: 4% a 10% (Sua faixa do Simples Nacional)
    private BigDecimal percentualImpostosVenda;

    // Ex: 20% (Quanto você QUER lucrar livre no bolso no mínimo)
    private BigDecimal margemLucroAlvo;

    // Singleton no banco (só teremos 1 registro)
}
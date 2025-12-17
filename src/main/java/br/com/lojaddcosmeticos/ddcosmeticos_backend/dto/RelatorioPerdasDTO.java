package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RelatorioPerdasDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;
    private String motivo; // ex: "FURTO", "VALIDADE", "QUEBRA"
    private Long quantidadeOcorrencias;
    private BigDecimal valorTotalPrejuizo; // Soma do custo dos produtos perdidos
}
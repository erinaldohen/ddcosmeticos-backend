package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RelatorioPerdasDTO {
    private String motivo; // ex: "FURTO", "VALIDADE", "QUEBRA"
    private Long quantidadeOcorrencias;
    private BigDecimal valorTotalPrejuizo; // Soma do custo dos produtos perdidos
}
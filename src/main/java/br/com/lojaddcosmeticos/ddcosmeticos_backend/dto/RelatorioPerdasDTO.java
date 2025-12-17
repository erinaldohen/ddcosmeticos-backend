package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

public record RelatorioPerdasDTO(
        String motivo, // ex: "FURTO", "VALIDADE"
        Long quantidadeOcorrencias,
        BigDecimal valorTotalPrejuizo
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
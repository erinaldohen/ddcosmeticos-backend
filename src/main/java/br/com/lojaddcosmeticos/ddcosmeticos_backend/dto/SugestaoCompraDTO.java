package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record SugestaoCompraDTO(
        String codigoBarras,
        String descricao,
        String marca,
        Integer estoqueAtual,
        Integer estoqueMinimo,
        Integer quantidadeSugerida,
        String nivelUrgencia,
        BigDecimal custoEstimado
) {
    // Records geram automaticamente:
    // - Construtor com todos os argumentos
    // - Getters (ex: codigoBarras())
    // - equals(), hashCode() e toString()
}
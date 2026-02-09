package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

// Record simples para transportar os dados do gráfico/relatório
public record ResumoDespesaDTO(
        String categoria, // Aqui enviaremos o Nome do Fornecedor
        Long quantidade,
        BigDecimal valorTotal
) {}
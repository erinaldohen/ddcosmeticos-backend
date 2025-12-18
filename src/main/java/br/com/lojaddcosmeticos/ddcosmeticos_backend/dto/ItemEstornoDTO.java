package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record ItemEstornoDTO(
        String codigoBarras,
        BigDecimal quantidade
) {}
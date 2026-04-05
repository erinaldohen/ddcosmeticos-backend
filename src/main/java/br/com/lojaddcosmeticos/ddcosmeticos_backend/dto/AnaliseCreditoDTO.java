package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record AnaliseCreditoDTO(
        boolean bloqueado,
        String motivo,
        BigDecimal debitosAtraso
) {}
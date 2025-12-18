package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record ResumoPagamentoDTO(
        String formaPagamento,
        Long quantidade,
        BigDecimal valorTotal
) {}
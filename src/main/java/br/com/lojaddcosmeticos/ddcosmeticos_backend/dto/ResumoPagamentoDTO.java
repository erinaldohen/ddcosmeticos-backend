package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record ResumoPagamentoDTO(
        String formaPagamento,
        Long quantidade,
        BigDecimal valorTotal
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
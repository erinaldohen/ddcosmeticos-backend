package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record DevedorResumoDTO(
        Long idCliente,
        String nome,
        String documento,
        String telefone,
        BigDecimal totalDevido,
        BigDecimal totalAtrasado
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record DashboardResponseDTO(
        BigDecimal faturamentoHoje,
        long vendasHoje,
        BigDecimal recebiveisSeteDias,
        BigDecimal pagamentosSeteDias,
        long produtosAbaixoMinimo,
        BigDecimal saldoProjetadoSeteDias
) {}
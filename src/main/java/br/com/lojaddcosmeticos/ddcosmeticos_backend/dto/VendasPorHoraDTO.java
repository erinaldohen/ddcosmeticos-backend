package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record VendasPorHoraDTO(
        int hora,
        BigDecimal valorTotal,
        long quantidadeVendas
) {}
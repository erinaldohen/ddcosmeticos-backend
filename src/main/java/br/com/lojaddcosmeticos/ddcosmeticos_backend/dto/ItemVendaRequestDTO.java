package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record ItemVendaRequestDTO(
        String codigoBarras,
        BigDecimal quantidade
) {}
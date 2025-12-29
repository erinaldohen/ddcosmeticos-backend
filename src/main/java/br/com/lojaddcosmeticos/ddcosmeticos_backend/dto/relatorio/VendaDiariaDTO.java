package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendaDiariaDTO(
        LocalDate data,
        BigDecimal totalVendido,
        Long quantidadeVendas
) {}
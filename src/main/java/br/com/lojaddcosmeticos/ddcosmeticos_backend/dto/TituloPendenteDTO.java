package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TituloPendenteDTO(
        Long idConta,
        Long idVendaOriginal,
        LocalDate dataVencimento,
        BigDecimal valor,
        long diasEmAtraso
) {}
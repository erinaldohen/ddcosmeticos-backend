package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record FechamentoCaixaRequestDTO(
        @NotNull(message = "O valor contado em espécie é obrigatório.")
        @PositiveOrZero(message = "O valor contado não pode ser negativo.")
        BigDecimal valorFisicoInformado,

        String justificativaDiferenca
) {}
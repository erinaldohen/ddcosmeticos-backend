package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para a solicitação formal de fecho de caixa.
 * Inclui o valor físico contado pelo operador para validação de integridade.
 */
public record ConfirmacaoFechamentoDTO(

        @NotNull(message = "A data do fecho é obrigatória.")
        LocalDate data,

        @NotNull(message = "O valor contado em espécie é obrigatório.")
        @PositiveOrZero(message = "O valor contado não pode ser negativo.")
        BigDecimal valorContadoEmEspecie,

        String justificativaDiferenca // Obrigatório via lógica de serviço se a quebra for > R$ 5,00
) {}
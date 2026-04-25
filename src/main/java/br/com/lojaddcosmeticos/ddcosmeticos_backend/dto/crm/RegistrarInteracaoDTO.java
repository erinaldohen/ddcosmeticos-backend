package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RegistrarInteracaoDTO(
        @NotNull(message = "O ID do cliente é obrigatório")
        Long clienteId,

        @NotBlank(message = "O tipo de abordagem é obrigatório")
        @Pattern(regexp = "^[a-zA-Z0-9\\sÀ-ÿ.,-]*$", message = "Caracteres inválidos detectados")
        String tipoAbordagem, // REPOSICAO, CHURN, etc.

        @NotBlank(message = "O resultado é obrigatório")
        @Pattern(regexp = "^[a-zA-Z0-9\\sÀ-ÿ.,-]*$", message = "Caracteres inválidos detectados")
        String resultado,

        @Pattern(regexp = "^[a-zA-Z0-9\\sÀ-ÿ.,-]*$", message = "Caracteres inválidos detectados")
        String observacao
) {
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegistrarInteracaoDTO(
        @NotNull(message = "O ID do cliente é obrigatório")
        Long clienteId,

        @NotBlank(message = "O tipo de abordagem é obrigatório")
        String tipoAbordagem, // REPOSICAO, CHURN, etc.

        @NotBlank(message = "O resultado é obrigatório")
        String resultado,

        String observacao
) {
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClienteDTO(
        Long id,

        @NotBlank(message = "O nome/razão social é obrigatório")
        String nome,

        String nomeFantasia,

        // 🔥 MUDANÇA: Retirado o NotBlank. Agora a obrigatoriedade é decidida pelo Service (PF x PJ).
        String documento,

        String inscricaoEstadual,

        // 🔥 MUDANÇA: Telefone agora é vital
        String telefone,

        String endereco,

        @NotNull(message = "O limite de crédito é obrigatório")
        @PositiveOrZero
        BigDecimal limiteCredito,

        LocalDateTime dataCadastro,
        boolean ativo
) {}
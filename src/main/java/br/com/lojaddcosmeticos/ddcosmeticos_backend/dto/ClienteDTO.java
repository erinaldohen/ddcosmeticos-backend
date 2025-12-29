package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClienteDTO(
        Long id,

        @NotBlank(message = "O nome/razão social é obrigatório")
        String nome, // Serve para Nome (PF) ou Razão Social (PJ)

        String nomeFantasia, // Novo: Para PJ

        @NotBlank(message = "O documento (CPF/CNPJ) é obrigatório")
        @Pattern(regexp = "([0-9]{11}|[0-9]{14})", message = "O documento deve ter 11 (CPF) ou 14 (CNPJ) dígitos numéricos")
        String documento, // Mudamos de 'cpf' para 'documento'

        // Novo: Obrigatório para PJ que não é isento
        String inscricaoEstadual,

        String telefone,
        String endereco,

        @NotNull(message = "O limite de crédito é obrigatório")
        @PositiveOrZero
        BigDecimal limiteCredito,

        LocalDateTime dataCadastro,
        boolean ativo
) {}
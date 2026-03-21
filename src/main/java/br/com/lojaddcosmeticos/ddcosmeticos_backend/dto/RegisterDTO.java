package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de Registro convertido para Java Record.
 * Records são imutáveis e ideais para o trânsito de dados (DTOs).
 * Substitui o uso do Lombok (@Data) e do Serializable tradicional.
 */
public record RegisterDTO(

        @NotBlank(message = "Matrícula é obrigatória")
        String matricula,

        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        String senha,

        @NotNull(message = "Perfil é obrigatório")
        PerfilDoUsuario perfil
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
// Se estiver usando Java Records (recomendado para DTOs no Java 17+), não precisa de @Data ou Serializable
// Mas mantendo seu estilo atual com classe e Lombok:
import lombok.Data;

import java.io.Serializable;

@Data
public class RegisterDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Matrícula é obrigatória")
    private String matricula;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    // --- NOVO CAMPO OBRIGATÓRIO ---
    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    private String email;

    @NotBlank(message = "Senha é obrigatória")
    private String senha;

    @NotNull(message = "Perfil é obrigatório")
    private PerfilDoUsuario perfil; // Ex: ROLE_ADMIN, ROLE_USUARIO
}
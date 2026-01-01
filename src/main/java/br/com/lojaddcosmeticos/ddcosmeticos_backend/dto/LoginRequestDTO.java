// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/LoginRequestDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class LoginRequestDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "O login é obrigatório.")
    private String login;

    @NotBlank(message = "A senha é obrigatória.")
    private String senha;
}
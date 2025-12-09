// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/LoginRequestDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;

/**
 * DTO para receber a matrícula (username) e a senha do usuário no login.
 */
@Data
public class LoginRequestDTO {
    private String matricula; // Corresponde ao username
    private String senha;
}
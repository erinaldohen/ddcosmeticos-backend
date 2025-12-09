// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/LoginResponseDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;

/**
 * DTO para retornar o token JWT e as informações básicas do usuário após o login bem-sucedido.
 */
@Data
public class LoginResponseDTO {
    private String token;
    private String matricula;
    private String nome;
    private String perfil;
}
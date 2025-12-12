package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor // Gera um construtor com TODOS os campos automaticamente
public class LoginResponseDTO {

    private String token;
    private String matricula;
    private String nome;
    private String perfil;
}
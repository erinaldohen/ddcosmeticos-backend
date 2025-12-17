package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor // Gera um construtor com TODOS os campos automaticamente
public class LoginResponseDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    private String token;
    private String matricula;
    private String nome;
    private String perfil;
}
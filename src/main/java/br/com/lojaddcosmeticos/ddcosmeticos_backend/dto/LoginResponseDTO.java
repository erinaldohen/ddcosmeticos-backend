package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;
    private String matricula; // Campo solicitado
    private String nome;
    private String perfil;
}
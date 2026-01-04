package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

public class LoginRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "A matrícula é obrigatória.")
    private String matricula;

    @NotBlank(message = "A senha é obrigatória.")
    private String senha;

    // --- GETTERS E SETTERS MANUAIS (Para garantir que o JSON seja lido) ---

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
}
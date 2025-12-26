package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.io.Serializable;

// O Record já cria construtor, getters (sem o prefixo 'get'), equals, hashcode e toString.
public record LoginResponseDTO(
        String token,
        String matricula,
        String nome,
        String perfil
) implements Serializable {
    // É boa prática manter o serialVersionUID se for implements Serializable
    private static final long serialVersionUID = 1L;
}
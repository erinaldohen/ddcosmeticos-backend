package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;

// Ordem exata dos campos: ID, Nome, Email, Matricula, Perfil (Enum), Ativo (boolean)
public record UsuarioResponseDTO(
        Long id,
        String nome,
        String email,
        String matricula,
        PerfilDoUsuario perfil,
        boolean ativo
) {}
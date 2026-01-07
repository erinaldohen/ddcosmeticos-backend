package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;

public record UsuarioResponseDTO(
        Long id,
        String login,
        String nome,
        PerfilDoUsuario role,
        boolean ativo
) {}
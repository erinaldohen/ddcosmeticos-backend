package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;

public record UsuarioResponseDTO(
        Long id,
        String login,
        String nome, // Adicionei 'nome' se tiver no model, se não, pode remover
        PerfilDoUsuario role,
        boolean ativo
) {}
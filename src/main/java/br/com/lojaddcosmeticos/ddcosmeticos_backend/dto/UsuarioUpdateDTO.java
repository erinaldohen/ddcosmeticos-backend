package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;

public record UsuarioUpdateDTO(
        String nome,
        String email, // Adicionado pois o controller tenta atualizar
        PerfilDoUsuario role,
        String novaSenha // Opcional
) {}
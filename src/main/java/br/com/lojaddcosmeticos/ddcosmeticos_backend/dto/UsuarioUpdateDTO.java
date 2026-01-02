package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;

public record UsuarioUpdateDTO(
        String nome,
        PerfilDoUsuario role,
        String novaSenha // Opcional (só manda se for trocar)
) {}
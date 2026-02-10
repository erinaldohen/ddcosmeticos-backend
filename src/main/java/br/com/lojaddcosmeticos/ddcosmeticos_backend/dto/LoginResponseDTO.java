package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

// Retorna o token (pode ser null se estiver usando Cookie) e o objeto do usuário
public record LoginResponseDTO(String token, UsuarioResponseDTO usuario) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.time.LocalDateTime;

// Record para mapear o JSON do Frontend: { acao, detalhes, usuario, dataHora }
public record AuditoriaRequestDTO(
        String acao,
        String detalhes,
        String usuario,
        LocalDateTime dataHora
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoEvento; // Importe o Enum
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record AuditoriaRequestDTO(
        // Agora é fortemente tipado com o Enum
        TipoEvento tipoEvento,

        String mensagem,
        String usuarioResponsavel,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime dataHora
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NfceResponseDTO {
    private String chaveAcesso;
    private String numeroNota;
    private String serie;
    private String status;
    private String motivo;
    private String xml;
    private LocalDateTime dataEmissao;
}
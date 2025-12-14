package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NfceResponseDTO {
    private String xml;
    private String status;       // "AUTORIZADO", "REJEITADO"
    private String protocolo;    // Protocolo da SEFAZ
    private String mensagem;     // Mensagem de sucesso ou erro
}
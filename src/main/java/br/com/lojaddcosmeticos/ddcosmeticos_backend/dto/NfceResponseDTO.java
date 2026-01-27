package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NfceResponseDTO {
    private Long idVenda;
    private String status;
    private String mensagem;
    private String chaveAcesso;
    private String protocolo;
    private String xml;
    private String urlPdf;
}
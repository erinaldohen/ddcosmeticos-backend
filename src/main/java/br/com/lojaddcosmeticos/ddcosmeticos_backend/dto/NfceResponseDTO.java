package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NfceResponseDTO {

    private boolean autorizada;
    private String statusSefaz;

    /**
     * XML assinado digitalmente (para armazenamento e QR Code)
     */
    private String xmlNfce;

    private String numeroNota;

    /**
     * Número do Protocolo de Autorização da SEFAZ.
     * Essencial para consultas futuras e validade jurídica.
     */
    private String protocolo; // <--- O CAMPO QUE FALTAVA
}
// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/NfceResponseDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO que representa a resposta do processamento da Nota Fiscal de Consumidor Eletrônica (NFC-e).
 * Usado para informar ao PDV o status fiscal da venda.
 */
@Data
@Builder
public class NfceResponseDTO {

    /**
     * Chave de acesso única da NFC-e (44 dígitos).
     */
    private String chaveDeAcesso;

    /**
     * Número da NFC-e.
     */
    private String numeroNota;

    /**
     * Mensagem de status da SEFAZ (ex: "Autorizado o uso da NFC-e").
     */
    private String statusSefaz;

    /**
     * Indica se a nota foi autorizada com sucesso.
     */
    private boolean autorizada;

    /**
     * O XML completo da nota fiscal (pode ser usado para armazenamento ou impressão).
     */
    private String xmlNfce;

    // Outros campos como QR Code e protocolo de autorização seriam adicionados aqui.
}
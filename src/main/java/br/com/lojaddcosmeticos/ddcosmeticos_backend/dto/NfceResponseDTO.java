package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.io.Serializable;

public record NfceResponseDTO(
        String xml,
        String status,
        String protocolo,
        String mensagem
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NfceResponseDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;
    private String xml;
    private String status;       // "AUTORIZADO", "REJEITADO"
    private String protocolo;    // Protocolo da SEFAZ
    private String mensagem;     // Mensagem de sucesso ou erro
}
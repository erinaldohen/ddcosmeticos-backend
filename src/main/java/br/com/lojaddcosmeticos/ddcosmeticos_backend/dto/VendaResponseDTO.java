package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VendaResponseDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;
    private Long idVenda;
    private LocalDateTime dataVenda;
    private BigDecimal valorTotal;
    private BigDecimal desconto;
    private String operador;
    private int totalItens;

    // Novo campo para avisar o caixa
    private List<String> alertas;

    // Status para saber se a nota foi gerada ou ficou pendente
    private String statusFiscal;
}
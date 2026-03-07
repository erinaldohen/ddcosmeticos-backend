package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de Resposta (Response) após o Fechamento do Caixa.
 * Utilizado para exibir o Relatório de Fechamento Cego no Frontend.
 */
public record ConfirmacaoFechamentoDTO(
        Long caixaId,
        String operador,
        LocalDateTime dataFechamento,
        BigDecimal saldoEsperado,
        BigDecimal valorInformado,
        BigDecimal diferenca
) {}
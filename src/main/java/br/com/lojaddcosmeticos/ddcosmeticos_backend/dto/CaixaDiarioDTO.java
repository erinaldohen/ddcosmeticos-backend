package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CaixaDiarioDTO(
        Long id,
        String status,
        LocalDateTime dataAbertura,
        LocalDateTime dataFechamento,
        String operadorNome,
        BigDecimal saldoInicial,
        BigDecimal saldoAtual,
        BigDecimal totalEntradas,
        BigDecimal totalSaidas,
        BigDecimal totalVendasDinheiro,
        BigDecimal totalVendasPix,
        BigDecimal totalVendasCredito,
        BigDecimal totalVendasDebito,
        BigDecimal saldoEsperadoSistema,
        BigDecimal valorFisicoInformado,
        BigDecimal diferencaCaixa
) {}
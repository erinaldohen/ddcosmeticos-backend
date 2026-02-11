package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record CaixaDiarioDTO(
        Long id,
        String status,
        BigDecimal saldoInicial,
        BigDecimal saldoAtual, // Adicionado para facilitar
        BigDecimal totalEntradas, // Suprimentos
        BigDecimal totalSaidas,   // Sangrias
        BigDecimal totalVendasDinheiro,
        BigDecimal totalVendasPix,
        BigDecimal totalVendasCredito, // Separado
        BigDecimal totalVendasDebito   // Separado
) {}
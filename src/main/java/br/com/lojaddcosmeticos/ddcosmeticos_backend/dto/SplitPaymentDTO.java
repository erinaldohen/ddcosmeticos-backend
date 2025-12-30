package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record SplitPaymentDTO(
        BigDecimal valorTotalVenda,
        BigDecimal valorLiquidoLojista, // O que cai na sua conta
        BigDecimal valorImpostoRetido,  // O que vai para o Governo (IBS/CBS)
        BigDecimal aliquotaEfetiva,     // % total aplicado
        String mensagem                 // Ex: "Split aplicado com redução de 60%"
) {}
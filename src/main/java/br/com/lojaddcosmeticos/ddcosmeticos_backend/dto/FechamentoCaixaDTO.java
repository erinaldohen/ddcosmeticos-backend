package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record FechamentoCaixaDTO(
        LocalDate data,
        long quantidadeVendas,
        BigDecimal totalVendasBruto,

        // Novas métricas de fluxo manual
        BigDecimal totalSuprimentos, // (+) Dinheiro que entrou para troco
        BigDecimal totalSangrias,    // (-) Dinheiro retirado

        Map<String, BigDecimal> totaisPorFormaPagamento,

        // O valor real que deve estar na gaveta de dinheiro
        BigDecimal saldoFinalDinheiroEmEspecie
) {}
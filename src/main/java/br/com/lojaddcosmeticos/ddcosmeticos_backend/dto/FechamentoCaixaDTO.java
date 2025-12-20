package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Record para transporte de dados do fechamento.
 * A anotação @Builder permite o uso do padrão nomeado (FechamentoCaixaDTO.builder()...)
 */
@Builder
public record FechamentoCaixaDTO(
        LocalDate data,
        long quantidadeVendas,
        BigDecimal totalVendasBruto,
        BigDecimal totalSuprimentos,
        BigDecimal totalSangrias,
        Map<String, BigDecimal> totaisPorFormaPagamento,
        BigDecimal saldoFinalDinheiroEmEspecie
) {}
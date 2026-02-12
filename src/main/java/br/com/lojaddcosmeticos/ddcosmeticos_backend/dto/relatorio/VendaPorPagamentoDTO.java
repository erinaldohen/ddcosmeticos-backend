package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import java.math.BigDecimal;

public record VendaPorPagamentoDTO(
        FormaDePagamento formaPagamento, // Mudou de String para Enum
        BigDecimal valorTotal,
        Long quantidade
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import java.math.BigDecimal;

public record PagamentoResponseDTO(
        FormaDePagamento formaPagamento,
        BigDecimal valor,
        Integer parcelas
) {}
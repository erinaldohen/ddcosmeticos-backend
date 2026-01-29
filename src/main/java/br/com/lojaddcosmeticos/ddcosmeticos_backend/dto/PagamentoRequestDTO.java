package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import java.math.BigDecimal;

public record PagamentoRequestDTO(
        FormaDePagamento formaPagamento,
        BigDecimal valor,
        Integer parcelas
) {}
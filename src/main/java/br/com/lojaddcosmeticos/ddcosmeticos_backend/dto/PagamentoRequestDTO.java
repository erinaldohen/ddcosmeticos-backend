package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import java.math.BigDecimal;

public record PagamentoRequestDTO(
        BigDecimal valor,
        FormaDePagamento formaPagamento, // Deve ser o Enum, não String
        String codigoVale // Opcional, para trocas
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import java.math.BigDecimal;

public record PagamentoRequestDTO(
        BigDecimal valor,
        FormaPagamento formaPagamento, // Deve ser o Enum, não String
        String codigoVale // Opcional, para trocas
) {}
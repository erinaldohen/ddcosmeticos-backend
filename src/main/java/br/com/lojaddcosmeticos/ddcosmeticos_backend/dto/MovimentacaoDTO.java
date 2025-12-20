package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;

import java.math.BigDecimal;

public record MovimentacaoDTO(
        BigDecimal valor,
        TipoMovimentacaoCaixa tipo,
        String motivo
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import java.math.BigDecimal;

public record ProdutoRankingDTO(
        String produto,
        BigDecimal valorTotal,
        Long quantidade,
        String unidade
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import java.math.BigDecimal;

public record ProdutoRankingDTO(
        String codigoBarras,
        String nomeProduto,
        Long quantidadeVendida,
        BigDecimal totalFaturado
) {}
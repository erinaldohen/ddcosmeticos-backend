package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record ProdutoExternoDTO(
        String ean,
        String nome,
        String ncm,
        String cest,
        String urlImagem,
        BigDecimal precoMedio
) {}
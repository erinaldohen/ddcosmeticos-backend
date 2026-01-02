package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record ProdutoListagemDTO(
        Long id,
        String descricao,
        BigDecimal precoVenda,
        String urlImagem,
        Integer quantidadeEmEstoque, // <--- Adicionado
        Boolean ativo,
        String codigoBarras,
        String marca,
        String ncm
) {}
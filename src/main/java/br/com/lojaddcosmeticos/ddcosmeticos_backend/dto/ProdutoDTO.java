package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ProdutoDTO(
        @NotBlank String nome,
        String descricao,
        @NotNull @PositiveOrZero BigDecimal precoCusto,
        @NotNull @PositiveOrZero BigDecimal precoVenda,
        @NotNull @PositiveOrZero Integer quantidadeEstoque,
        @NotBlank String codigoBarras,

        // Novos campos fiscais e visuais
        String ncm,
        String cest,
        String urlImagem,

        boolean ativo
) {}
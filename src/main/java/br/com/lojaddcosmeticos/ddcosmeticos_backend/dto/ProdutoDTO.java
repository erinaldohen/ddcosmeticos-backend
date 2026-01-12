package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProdutoDTO(
        Long id,

        @NotBlank(message = "O código de barras é obrigatório")
        @Size(max = 20, message = "Código de barras muito longo")
        String codigoBarras,

        @NotBlank(message = "A descrição do produto é obrigatória")
        String descricao,

        String unidade,

        @NotNull(message = "Preço de custo obrigatório")
        @PositiveOrZero
        BigDecimal precoCusto,

        @NotNull(message = "Preço de venda obrigatório")
        @PositiveOrZero
        BigDecimal precoVenda,

        @PositiveOrZero
        Integer quantidadeEstoque, // Total

        // --- NOVOS CAMPOS EXPOSTOS (Adicione isto) ---
        Integer estoqueFiscal,
        Integer estoqueNaoFiscal,
        // ---------------------------------------------

        @PositiveOrZero
        Integer estoqueMinimo,

        @Size(max = 20, message = "NCM deve ter no máximo 20 dígitos")
        String ncm,

        String marca,
        String categoria,
        String subcategoria,
        String cest,
        String cst,
        Boolean monofasico,
        TipoTributacaoReforma classificacaoReforma,
        String urlImagem,
        boolean ativo
) {
    public ProdutoDTO(Produto produto) {
        this(
                produto.getId(),
                produto.getCodigoBarras(),
                produto.getDescricao(),
                produto.getUnidade(),
                produto.getPrecoCusto(),
                produto.getPrecoVenda(),
                produto.getQuantidadeEmEstoque(),

                // Mapeando os novos campos do Modelo para o DTO
                produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0,
                produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0,

                produto.getEstoqueMinimo(),
                produto.getNcm(),
                produto.getMarca(),
                produto.getCategoria(),
                produto.getSubcategoria(),
                produto.getCest(),
                produto.getCst(),
                produto.isMonofasico(),
                produto.getClassificacaoReforma(),
                produto.getUrlImagem(),
                produto.isAtivo()
        );
    }
}
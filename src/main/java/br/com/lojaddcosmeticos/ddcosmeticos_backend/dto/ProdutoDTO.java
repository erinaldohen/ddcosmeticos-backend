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
        Integer quantidadeEstoque,

        @PositiveOrZero
        Integer estoqueMinimo,

        @Size(max = 20, message = "NCM deve ter no máximo 20 dígitos")
        String ncm,

        // Dados de Classificação
        String marca,
        String categoria,
        String subcategoria,

        String cest,

        // --- NOVOS CAMPOS FISCAIS ---
        String cst,
        Boolean monofasico, // Método gerado: monofasico()
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
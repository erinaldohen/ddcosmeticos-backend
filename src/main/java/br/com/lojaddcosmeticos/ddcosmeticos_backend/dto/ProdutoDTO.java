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

        String cest,
        String cst,
        Boolean monofasico,

        // --- NOVO CAMPO DA REFORMA ---
        TipoTributacaoReforma classificacaoReforma,

        String urlImagem,
        boolean ativo
) {
        // Construtor de conversão Entity -> DTO
        public ProdutoDTO(Produto produto) {
                this(
                        produto.getId(),
                        produto.getCodigoBarras(),
                        produto.getDescricao(),
                        produto.getPrecoCusto(),
                        produto.getPrecoVenda(),
                        produto.getQuantidadeEmEstoque(),
                        produto.getEstoqueMinimo(),
                        produto.getNcm(),
                        produto.getCest(),
                        produto.getCst(),
                        produto.isMonofasico(),
                        // Se for nulo no banco (legado), assume PADRAO
                        produto.getClassificacaoReforma() != null ? produto.getClassificacaoReforma() : TipoTributacaoReforma.PADRAO,
                        produto.getUrlImagem(),
                        produto.isAtivo()
                );
        }
}
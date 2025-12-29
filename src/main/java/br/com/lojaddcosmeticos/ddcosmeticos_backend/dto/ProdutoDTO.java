package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProdutoDTO(
        // ID é opcional na criação, mas útil na atualização
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

        // Pode ser nulo se não houver controle de estoque inicial
        @PositiveOrZero
        Integer quantidadeEstoque,

        @PositiveOrZero
        Integer estoqueMinimo,

        // --- DADOS FISCAIS ---
        @Size(max = 20, message = "NCM deve ter no máximo 20 dígitos") // Ajustado para bater com o Model
        String ncm,

        String cest,

        // Adicionado para permitir override manual da inteligência fiscal
        String cst,

        // Adicionado para permitir override manual
        Boolean monofasico,

        String urlImagem,

        boolean ativo
) {
        // --- CONSTRUTOR DE CONVERSÃO (Resolve o erro do ProdutoService) ---
        public ProdutoDTO(Produto produto) {
                this(
                        produto.getId(),
                        produto.getCodigoBarras(),
                        produto.getDescricao(),
                        produto.getPrecoCusto(),
                        produto.getPrecoVenda(),
                        produto.getQuantidadeEmEstoque(), // Mapeia 'quantidadeEmEstoque' do Model para 'quantidadeEstoque' do DTO
                        produto.getEstoqueMinimo(),
                        produto.getNcm(),
                        produto.getCest(),
                        produto.getCst(),
                        produto.isMonofasico(),
                        produto.getUrlImagem(),
                        produto.isAtivo()
                );
        }
}
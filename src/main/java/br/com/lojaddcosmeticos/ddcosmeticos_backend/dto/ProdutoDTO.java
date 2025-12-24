package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

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

        // UNIFICADO: Removemos 'nome' e tornamos 'descricao' obrigatória
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

        // --- DADOS FISCAIS ---
        @Size(max = 8, message = "NCM deve ter no máximo 8 dígitos")
        String ncm,

        String cest,

        // Adicionado para permitir override manual da inteligência fiscal
        String cst,

        // Adicionado para permitir override manual
        Boolean monofasico,

        String urlImagem,

        boolean ativo
) {}
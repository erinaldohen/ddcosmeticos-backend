package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProdutoDTO(
        Long id,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 150, message = "A descrição não pode exceder 150 caracteres")
        String descricao,

        String codigoBarras,
        String sku, // NOVO: Código interno

        String marca,
        String categoria,
        String subcategoria,
        String unidade,

        // INVENTÁRIO & RASTREABILIDADE (NOVOS)
        String lote, // NOVO

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate validade, // NOVO

        // FISCAL
        String ncm,
        String cest,
        String cst,

        @Size(max = 1, message = "A origem deve ter apenas 1 dígito")
        String origem,

        Boolean monofasico,

        TipoTributacaoReforma classificacaoReforma,

        Boolean impostoSeletivo,

        // FINANCEIRO
        @NotNull(message = "O preço de venda é obrigatório")
        BigDecimal precoVenda,

        BigDecimal precoCusto,

        // ESTOQUE
        Integer estoqueMinimo,
        Integer diasParaReposicao,

        String urlImagem,
        boolean ativo
) {
    /**
     * Construtor compacto para conversão de Entidade para DTO.
     * Atualizado para incluir SKU, Lote e Validade.
     */
    public ProdutoDTO(Produto p) {
        this(
                p.getId(),
                p.getDescricao(),
                p.getCodigoBarras(),
                p.getSku(),       // Mapeando SKU
                p.getMarca(),
                p.getCategoria(),
                p.getSubcategoria(),
                p.getUnidade(),
                p.getLote(),      // Mapeando Lote
                p.getValidade(),  // Mapeando Validade
                p.getNcm(),
                p.getCest(),
                p.getCst(),
                p.getOrigem(),
                p.isMonofasico(),
                p.getClassificacaoReforma(),
                p.isImpostoSeletivo(),
                p.getPrecoVenda(),
                p.getPrecoCusto(),
                p.getEstoqueMinimo(),
                p.getDiasParaReposicao(),
                p.getUrlImagem(),
                p.isAtivo()
        );
    }
}
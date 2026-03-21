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
        String descricao,

        String codigoBarras,
        String sku, // Código interno

        String marca,
        String categoria,
        String subcategoria,
        String unidade,

        // INVENTÁRIO & RASTREABILIDADE
        String lote,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate validade,

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
        Integer quantidadeEmEstoque, // Saldo total
        Integer estoqueFiscal,       // Com Nota
        Integer estoqueNaoFiscal,    // Sem Nota
        Integer estoqueMinimo,
        Integer diasParaReposicao,

        String urlImagem,
        boolean ativo,

        // 🚩 NOVO: Flag de revisão para produtos cadastrados rapidamente no PDV
        Boolean revisaoPendente
) {
    public ProdutoDTO(Produto p) {
        this(
                p.getId(),
                p.getDescricao(),
                p.getCodigoBarras(),
                p.getSku(),
                p.getMarca(),
                p.getCategoria(),
                p.getSubcategoria(),
                p.getUnidade(),
                p.getLote(),
                p.getValidade(),
                p.getNcm(),
                p.getCest(),
                p.getCst(),
                p.getOrigem(),
                p.isMonofasico(),
                p.getClassificacaoReforma(),
                p.getIsImpostoSeletivo(),
                p.getPrecoVenda(),
                p.getPrecoCusto(),
                p.getQuantidadeEmEstoque(),
                p.getEstoqueFiscal(),
                p.getEstoqueNaoFiscal(),
                p.getEstoqueMinimo(),
                p.getDiasParaReposicao(),
                p.getUrlImagem(),
                p.isAtivo(),
                p.getRevisaoPendente() // 🚩 Mapeamento da nova flag
        );
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para transporte e validação de dados de Produto.
 * Alinhado com a blindagem do modelo e regras de performance do banco.
 */
public record ProdutoDTO(
        Long id,

        @NotBlank(message = "A descrição do produto é obrigatória")
        @Size(min = 3, max = 255, message = "A descrição deve ter entre 3 e 255 caracteres")
        String descricao,

        String codigoBarras,

        String sku,

        String marca,

        String categoria,

        String subcategoria,

        String unidade,

        // INVENTÁRIO & RASTREABILIDADE
        String lote,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate validade,

        // FISCAL (Essencial para a SEFAZ)
        @Size(max = 10, message = "NCM inválido")
        String ncm,

        String cest,

        String cst,

        @Size(max = 1, message = "A origem deve ter apenas 1 dígito (ex: 0 para nacional)")
        String origem,

        Boolean monofasico,

        TipoTributacaoReforma classificacaoReforma,

        Boolean impostoSeletivo,

        // FINANCEIRO (Com travas de valor positivo)
        @NotNull(message = "O preço de venda é obrigatório")
        @PositiveOrZero(message = "O preço de venda não pode ser negativo")
        BigDecimal precoVenda,

        @PositiveOrZero(message = "O preço de custo não pode ser negativo")
        BigDecimal precoCusto,

        BigDecimal precoMedioPonderado,

        // ESTOQUE
        @Min(value = 0, message = "A quantidade em estoque não pode ser negativa")
        Integer quantidadeEmEstoque,

        Integer estoqueFiscal,

        Integer estoqueNaoFiscal,

        Integer estoqueMinimo,

        Integer diasParaReposicao,

        String urlImagem,

        boolean ativo,

        // 🚩 Flag de revisão para produtos cadastrados rapidamente no balcão/PDV
        Boolean revisaoPendente
) {
    /**
     * Construtor de mapeamento: Converte a Entidade JPA para o DTO de forma segura.
     */
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
                p.getIsMonofasico(), // Ajustado para o nome correto do campo booleano
                p.getClassificacaoReforma(),
                p.getIsImpostoSeletivo(),
                p.getPrecoVenda(),
                p.getPrecoCusto(),
                p.getPrecoMedioPonderado(),
                p.getQuantidadeEmEstoque(),
                p.getEstoqueFiscal(),
                p.getEstoqueNaoFiscal(),
                p.getEstoqueMinimo(),
                p.getDiasParaReposicao(),
                p.getUrlImagem(),
                p.isAtivo(),
                p.getRevisaoPendente()
        );
    }
}
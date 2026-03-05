package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import java.math.BigDecimal;

public record ItemVendaResponseDTO(
        Long produtoId,
        String produtoNome,
        String codigoBarras,
        BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal desconto
) {
    // CONSTRUTOR MÁGICO: Ensina o DTO a aceitar o objeto ItemVenda do banco e extrair os dados sozinho
    public ItemVendaResponseDTO(ItemVenda item) {
        this(
                item.getProduto() != null ? item.getProduto().getId() : null,

                // Busca o nome ou a descrição para não ficar "Produto Desconhecido"
                (item.getProduto() != null && item.getProduto().getDescricao() != null)
                        ? item.getProduto().getDescricao()
                        : (item.getProduto() != null ? item.getProduto().getDescricao() : "Produto Excluído"),

                // Busca o EAN
                (item.getProduto() != null && item.getProduto().getCodigoBarras() != null)
                        ? item.getProduto().getCodigoBarras()
                        : "Sem EAN",

                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getDesconto()
        );
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProdutoInventarioDTO(
        Long id,
        String descricao,
        String sku,
        String codigoBarras,
        String categoria,
        Integer quantidade,
        Integer estoqueMinimo,
        LocalDate validade,
        BigDecimal precoCusto,
        BigDecimal precoVenda,

        // 🔥 NOVO: Identificação do fornecedor para a lista de compras
        String fornecedorNome,

        String curvaABC,
        Double giroDiario,
        String tendencia,
        Integer sugestaoCompra
) {
    public ProdutoInventarioDTO(Produto p, String curvaABC, Double giroDiario, String tendencia, Integer sugestaoCompra) {
        this(
                p.getId(), p.getDescricao(), p.getSku(), p.getCodigoBarras(), p.getCategoria(),
                p.getQuantidadeEmEstoque(), p.getEstoqueMinimo(),
                p.getValidade() != null ? LocalDate.from(p.getValidade()) : null,
                p.getPrecoCusto(), p.getPrecoVenda(),
                // Busca o fornecedor se existir, senão define como "Sem Fornecedor"
                p.getFornecedor() != null ? p.getFornecedor().getNomeFantasia() : "Sem Fornecedor",
                curvaABC, giroDiario, tendencia, sugestaoCompra
        );
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ItemVendaRepository extends JpaRepository<ItemVenda, Long> {

    // Busca os itens de uma venda específica
    List<ItemVenda> findByVendaIdVenda(Long idVenda);

    // Conta quantas vezes um produto foi vendido
    long countByProduto(Produto produto);

    // =====================================================================================
    // SOLUÇÃO DE ALTA PERFORMANCE (PROJEÇÃO) PARA EVITAR ERRO DE COMPILAÇÃO NO HIBERNATE 6
    // =====================================================================================

    interface TopProdutoProjection {
        String getDescricao();
        Long getQuantidade();
        BigDecimal getTotal();
    }

    @Query("SELECT i.produto.descricao as descricao, " +
            "CAST(SUM(i.quantidade) AS long) as quantidade, " +
            "CAST(SUM(i.precoUnitario * i.quantidade) AS bigdecimal) as total " +
            "FROM ItemVenda i " +
            "WHERE i.venda.dataVenda >= :dataInicio " +
            "GROUP BY i.produto.descricao " +
            "ORDER BY SUM(i.precoUnitario * i.quantidade) DESC")
    List<TopProdutoProjection> findTopProdutos(@Param("dataInicio") LocalDateTime dataInicio, Pageable pageable);
}
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

    // =====================================================================================
    // 🔥 NOVOS MÉTODOS PARA O MOTOR DE INTELIGÊNCIA DO INVENTÁRIO (CURVA ABC E TENDÊNCIA)
    // =====================================================================================

    // Busca todas as vendas de itens após uma data específica (ex: últimos 30 dias)
    List<ItemVenda> findByVendaDataVendaAfter(LocalDateTime data);

    // Busca todas as vendas de itens num intervalo de datas (ex: do dia 30 ao dia 60 atrás)
    List<ItemVenda> findByVendaDataVendaBetween(LocalDateTime dataInicio, LocalDateTime dataFim);

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
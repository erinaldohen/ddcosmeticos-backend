package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
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
    // 🔥 MÉTODOS PARA O MOTOR DE INTELIGÊNCIA DO INVENTÁRIO (CURVA ABC E TENDÊNCIA)
    // =====================================================================================

    // NOTA DA ARQUITETURA: Evitar usar estes métodos se o objetivo for apenas somar ou contar.
    List<ItemVenda> findByVendaDataVendaAfter(LocalDateTime data);
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

    // =====================================================================================
    // 🧠 CÉREBRO DE MACHINE LEARNING (MARKET BASKET ANALYSIS - CROSS-SELL)
    // =====================================================================================

    @Query(value = """
        SELECT iv2.produto_id 
        FROM tb_item_venda iv1
        JOIN tb_item_venda iv2 ON iv1.venda_id = iv2.venda_id
        JOIN tb_produto p ON iv2.produto_id = p.id
        WHERE iv1.produto_id = :produtoBaseId 
          AND iv2.produto_id != :produtoBaseId 
          AND p.ativo = true
          AND p.quantidade_em_estoque > 0
        GROUP BY iv2.produto_id 
        ORDER BY COUNT(iv2.produto_id) DESC 
        LIMIT :limite
    """, nativeQuery = true)
    List<Long> descobrirProdutosMaisCompradosJuntos(@Param("produtoBaseId") Long produtoBaseId, @Param("limite") int limite);

    // =====================================================================================
    // 📊 QUERIES PARA O DASHBOARD (IMPACTO DA IA)
    // =====================================================================================

    @Query("SELECT CAST(COALESCE(SUM(i.precoUnitario * i.quantidade), 0) AS bigdecimal) " +
            "FROM ItemVenda i " +
            "WHERE i.influenciaIA != br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoInfluenciaIA.NENHUMA " +
            "AND i.venda.dataVenda >= :dataInicio")
    BigDecimal calcularRoiIAValor(@Param("dataInicio") LocalDateTime dataInicio);

    @Query("SELECT CAST(COALESCE(SUM(i.quantidade), 0) AS long) " +
            "FROM ItemVenda i " +
            "WHERE i.influenciaIA != br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoInfluenciaIA.NENHUMA " +
            "AND i.venda.dataVenda >= :dataInicio")
    Long calcularRoiIAItens(@Param("dataInicio") LocalDateTime dataInicio);

    // Novo método leve de agregação matemática
    @Query("SELECT COALESCE(SUM(i.quantidade), 0) FROM ItemVenda i WHERE i.produto.id = :produtoId AND i.venda.dataVenda BETWEEN :inicio AND :fim AND i.venda.statusNfce = 'AUTORIZADA'")
    Long somarQuantidadeVendidaNoPeriodo(@Param("produtoId") Long produtoId, @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
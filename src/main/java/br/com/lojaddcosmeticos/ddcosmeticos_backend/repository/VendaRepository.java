package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Long> {

    // =========================================================================
    // 1. CONSULTAS OPERACIONAIS & FILTROS (DASHBOARD)
    // =========================================================================

    @Query("SELECT DISTINCT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto WHERE v.idVenda = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);

    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA)")
    Long contarVendasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // Adicionado para resolver o erro no DashboardService
    @Query("SELECT v FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA) ORDER BY v.dataVenda DESC")
    List<Venda> buscarVendasPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Modifying
    @Query("UPDATE Venda v SET v.statusNfce = :novoStatus, v.motivoDoCancelamento = :motivo WHERE v.idVenda = :id")
    void atualizarStatusVenda(@Param("id") Long id, @Param("novoStatus") StatusFiscal novoStatus, @Param("motivo") String motivo);

    // =========================================================================
    // 2. MÉTRICAS FINANCEIRAS (DASHBOARD & BI)
    // =========================================================================

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA)")
    BigDecimal somarFaturamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT COALESCE(SUM((i.precoUnitario - i.custoUnitarioHistorico) * i.quantidade), 0) 
        FROM ItemVenda i 
        WHERE i.venda.dataVenda BETWEEN :inicio AND :fim 
        AND i.venda.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
    """)
    BigDecimal calcularLucroBrutoNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT COUNT(v) FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.valorTotal >= :min AND v.valorTotal <= :max
        AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
    """)
    Long contarVendasNaFaixa(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, @Param("min") double min, @Param("max") double max);

    // =========================================================================
    // 3. AGRUPAMENTOS PARA GRÁFICOS (BI)
    // =========================================================================

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO(
            CAST(v.dataVenda AS LocalDate), 
            CAST(SUM(v.valorTotal) AS BigDecimal),
            CAST(COUNT(v) AS Long)
        )
        FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        GROUP BY CAST(v.dataVenda AS LocalDate)
        ORDER BY CAST(v.dataVenda AS LocalDate) ASC
    """)
    List<VendaDiariaDTO> agruparVendasPorDia(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO(
            p.formaPagamento, 
            CAST(COUNT(p.id) AS Long),
            CAST(SUM(p.valor) AS BigDecimal)
        )
        FROM PagamentoVenda p 
        WHERE p.venda.dataVenda BETWEEN :inicio AND :fim 
        AND p.venda.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        GROUP BY p.formaPagamento
    """)
    List<VendaPorPagamentoDTO> agruparPorFormaPagamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.marca, 
            CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal), 
            CAST(SUM(i.quantidade) AS Long),
            'UN'
        ) 
        FROM ItemVenda i JOIN i.produto p
        WHERE i.venda.dataVenda BETWEEN :inicio AND :fim 
        AND i.venda.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        GROUP BY p.marca 
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingMarcas(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.categoria, 
            CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal), 
            CAST(SUM(i.quantidade) AS Long),
            'UN'
        ) 
        FROM ItemVenda i JOIN i.produto p
        WHERE i.venda.dataVenda BETWEEN :inicio AND :fim 
        AND i.venda.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        GROUP BY p.categoria 
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingCategorias(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    /**
     * ESTRATÉGIA DE CROSS-SELLING: Identifica pares de produtos vendidos juntos
     */
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.CrossSellDTO(
            CONCAT(p1.descricao, ' + ', p2.descricao),
            CAST(COUNT(*) AS Integer)
        )
        FROM ItemVenda i1
        JOIN i1.venda v
        JOIN i1.produto p1
        JOIN ItemVenda i2 ON i1.venda.idVenda = i2.venda.idVenda AND i1.id <> i2.id
        JOIN i2.produto p2
        WHERE v.dataVenda BETWEEN :inicio AND :fim
        AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        AND p1.id < p2.id
        GROUP BY p1.descricao, p2.descricao
        ORDER BY COUNT(*) DESC
    """)
    List<CrossSellDTO> buscarCrossSell(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    // =========================================================================
    // 4. MÉTODOS DE CONVENÇÃO (SPRING DATA)
    // =========================================================================

    List<Venda> findByStatusNfce(StatusFiscal statusNfce);
    List<Venda> findByStatusNfceIn(List<StatusFiscal> status); // Necessário para o NfceScheduler
    Page<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    /**
     * RANKING DE PRODUTOS: Usado pelo DashboardService para mostrar os itens mais vendidos.
     * DTO esperado: (String nome, BigDecimal valorTotal, Long quantidade, String unidade)
     */
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.descricao, 
            CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal), 
            CAST(SUM(i.quantidade) AS Long),
            'UN'
        ) 
        FROM ItemVenda i 
        JOIN i.produto p 
        JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        GROUP BY p.descricao
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingProdutos(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);
    // Adicione esta linha para resolver o erro no DashboardService:
    long countByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);
    // Adicione esta linha para curar o erro na linha 213 do DashboardService
    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA)")
    BigDecimal sumTotalVendaByDataVendaBetween(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
    @Query("SELECT SUM(v.valorTotal) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    BigDecimal calcularFaturamentoBruto(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
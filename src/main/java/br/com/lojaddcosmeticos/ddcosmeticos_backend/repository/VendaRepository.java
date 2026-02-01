package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
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

    // ... (Métodos básicos mantidos) ...
    long countByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);
    List<Venda> findByStatusNfce(StatusFiscal statusNfce);
    List<Venda> findByStatusNfceOrderByDataVendaDesc(StatusFiscal statusNfce);
    Page<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);
    List<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);

    @Query("SELECT DISTINCT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto WHERE v.idVenda = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);

    @Query("SELECT v FROM Venda v WHERE v.usuario.id = :usuarioId AND v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    List<Venda> buscarVendasDoUsuarioNoPeriodo(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    BigDecimal sumTotalVendaByDataVendaBetween(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // =========================================================================
    // MÉTODOS DASHBOARD (CORRIGIDOS PARA ACEITAR NULL)
    // =========================================================================

    // 1. Soma Faturamento
    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    BigDecimal somarFaturamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 2. Contar Vendas
    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    Long contarVendas(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 3. Lista para Gráficos
    @Query("SELECT v FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    List<Venda> buscarVendasPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 4. Gráfico Pizza (Pagamentos)
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO(
            CAST(p.formaPagamento AS string), 
            SUM(p.valor),
            COUNT(p)
        )
        FROM PagamentoVenda p 
        JOIN p.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')
        GROUP BY p.formaPagamento
    """)
    List<VendaPorPagamentoDTO> agruparPorPagamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 5. Ranking Produtos
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.descricao, 
            SUM(i.precoUnitario * i.quantidade), 
            COUNT(i.id),
            'UN'
        ) 
        FROM ItemVenda i 
        JOIN i.produto p 
        JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')
        GROUP BY p.descricao 
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingProdutos(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    // 6. Últimas Vendas (Top 5)
    @Query("SELECT v FROM Venda v WHERE (v.statusNfce IS NULL OR v.statusNfce != :statusIgnorado) ORDER BY v.dataVenda DESC LIMIT 5")
    List<Venda> findTop5Recentes(@Param("statusIgnorado") StatusFiscal statusIgnorado);

    // --- Outros Métodos (Mantidos) ---
    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    BigDecimal somarFaturamentoNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO(
            CAST(p.formaPagamento AS string), 
            SUM(p.valor),
            COUNT(DISTINCT v)
        )
        FROM PagamentoVenda p 
        JOIN p.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')
        GROUP BY p.formaPagamento
    """)
    List<VendaPorPagamentoDTO> agruparPorFormaPagamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO(
            CAST(v.dataVenda AS string), 
            SUM(v.valorTotal),
            COUNT(v)
        )
        FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')
        GROUP BY v.dataVenda
        ORDER BY v.dataVenda ASC
    """)
    List<VendaDiariaDTO> agruparVendasPorDia(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.marca, 
            SUM(i.precoUnitario * i.quantidade), 
            COUNT(DISTINCT v),
            'UN'
        ) 
        FROM ItemVenda i 
        JOIN i.produto p 
        JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')
        GROUP BY p.marca 
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingMarcas(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    @Modifying
    @Query("UPDATE Venda v SET v.statusNfce = :novoStatus, v.motivoDoCancelamento = :motivo WHERE v.idVenda = :id")
    void atualizarStatusVenda(@Param("id") Long id, @Param("novoStatus") StatusFiscal novoStatus, @Param("motivo") String motivo);
}
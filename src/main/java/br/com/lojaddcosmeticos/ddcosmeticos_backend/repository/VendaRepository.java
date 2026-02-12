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

    // =========================================================================
    // 1. CONSULTAS OPERACIONAIS
    // =========================================================================

    long countByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);
    List<Venda> findByStatusNfce(StatusFiscal statusNfce);
    Page<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    @Query("SELECT DISTINCT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto WHERE v.idVenda = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);

    @Query("SELECT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto LEFT JOIN FETCH v.pagamentos WHERE v.idVenda = :id")
    Optional<Venda> findByIdCompleto(@Param("id") Long id);

    @Query("SELECT v FROM Venda v WHERE v.usuario.id = :usuarioId AND v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    List<Venda> buscarVendasDoUsuarioNoPeriodo(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Modifying
    @Query("UPDATE Venda v SET v.statusNfce = :novoStatus, v.motivoDoCancelamento = :motivo WHERE v.idVenda = :id")
    void atualizarStatusVenda(@Param("id") Long id, @Param("novoStatus") StatusFiscal novoStatus, @Param("motivo") String motivo);

    // =========================================================================
    // 2. DASHBOARD / KPI
    // =========================================================================

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    BigDecimal somarFaturamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    BigDecimal somarFaturamentoTotal(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    BigDecimal sumTotalVendaByDataVendaBetween(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')")
    Long contarVendas(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT v FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA') ORDER BY v.dataVenda ASC")
    List<Venda> buscarVendasPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    List<Venda> findTop5ByOrderByDataVendaDesc();

    @Query("SELECT v FROM Venda v WHERE (v.statusNfce IS NULL OR v.statusNfce != :statusIgnorado) ORDER BY v.dataVenda DESC LIMIT 5")
    List<Venda> findTop5Recentes(@Param("statusIgnorado") StatusFiscal statusIgnorado);

    // =========================================================================
    // 3. RELATÓRIOS (COM CASTS EXPLÍCITOS PARA EVITAR ERRO DE CONSTRUTOR)
    // =========================================================================

    // DTO: (FormaPagamento, BigDecimal, Long)
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO(
            p.formaPagamento, 
            CAST(SUM(p.valor) AS BigDecimal),
            CAST(COUNT(p) AS Long)
        )
        FROM Venda v 
        JOIN v.pagamentos p
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')
        GROUP BY p.formaPagamento
    """)
    List<VendaPorPagamentoDTO> agruparPorFormaPagamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // DTO: (String, BigDecimal, Long, String)
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
        AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')
        GROUP BY p.descricao
        ORDER BY CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingProdutos(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    // DTO: (String, BigDecimal, Long, String)
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.marca, 
            CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal), 
            CAST(SUM(i.quantidade) AS Long),
            'UN'
        ) 
        FROM ItemVenda i 
        JOIN i.produto p 
        JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')
        GROUP BY p.marca 
        ORDER BY CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingMarcas(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    // DTO: (LocalDate, BigDecimal, Long)
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO(
            CAST(v.dataVenda AS LocalDate), 
            CAST(SUM(v.valorTotal) AS BigDecimal),
            CAST(COUNT(v) AS Long)
        )
        FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND (v.statusNfce IS NULL OR v.statusNfce != 'CANCELADA')
        GROUP BY CAST(v.dataVenda AS LocalDate)
        ORDER BY CAST(v.dataVenda AS LocalDate) ASC
    """)
    List<VendaDiariaDTO> agruparVendasPorDia(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Long> {

    // --- MÉTODOS SIMPLES (Usados pelo DashboardService) ---

    long countByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);

    @Query("SELECT COALESCE(SUM(v.totalVenda), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    BigDecimal sumTotalVendaByDataVendaBetween(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // --- OPERAÇÃO ---

    @Query("SELECT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto WHERE v.id = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);

    List<Venda> findByStatusFiscalOrderByDataVendaDesc(StatusFiscal statusFiscal);

    Page<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    @Query("SELECT DISTINCT v FROM Venda v JOIN FETCH v.itens i JOIN FETCH i.produto WHERE v.dataVenda BETWEEN :inicio AND :fim")
    List<Venda> buscarVendasComItensParaRelatorio(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // --- RELATÓRIOS AVANÇADOS (DTOs) ---

    // [ADICIONADO] Correção para o RelatorioService
    @Query("SELECT COALESCE(SUM(v.totalVenda), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    BigDecimal somarFaturamentoNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO(
            v.dataVenda, 
            SUM(v.totalVenda),
            COUNT(v) 
        )
        FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        GROUP BY v.dataVenda
        ORDER BY v.dataVenda ASC
    """)
    List<VendaDiariaDTO> agruparVendasPorDia(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO(
            v.formaPagamento, 
            SUM(v.totalVenda),
            COUNT(v)
        )
        FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        GROUP BY v.formaPagamento
    """)
    List<VendaPorPagamentoDTO> agruparPorFormaPagamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.marca, 
            SUM(i.precoUnitario * i.quantidade), 
            COUNT(v)
        ) 
        FROM ItemVenda i JOIN i.produto p JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        GROUP BY p.marca 
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingMarcas(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);
}
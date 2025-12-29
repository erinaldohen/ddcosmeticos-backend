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

    @Query("SELECT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto WHERE v.id = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);

    Page<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    // CORREÇÃO: Removida ambiguidade e garantida compatibilidade
    @Query("SELECT v FROM Venda v LEFT JOIN FETCH v.itens WHERE v.dataVenda BETWEEN :inicio AND :fim")
    List<Venda> buscarPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT SUM(v.totalVenda) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    BigDecimal somarVendasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    Long contarVendasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    List<Venda> findByStatusFiscalOrderByDataVendaDesc(StatusFiscal status);

    // ==================================================================================
    // RELATÓRIOS (Queries JPQL Validadas)
    // ==================================================================================

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO(
            CAST(v.dataVenda AS LocalDate),
            SUM(v.totalVenda),
            COUNT(v)
        )
        FROM Venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim
        AND v.statusFiscal <> 'CANCELADA'
        AND v.statusFiscal <> 'ORCAMENTO'
        GROUP BY CAST(v.dataVenda AS LocalDate)
        ORDER BY CAST(v.dataVenda AS LocalDate) ASC
    """)
    List<VendaDiariaDTO> relatorioVendasPorDia(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO(
            v.formaPagamento,
            SUM(v.totalVenda),
            COUNT(v)
        )
        FROM Venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim
        AND v.statusFiscal <> 'CANCELADA'
        AND v.statusFiscal <> 'ORCAMENTO'
        GROUP BY v.formaPagamento
    """)
    List<VendaPorPagamentoDTO> relatorioVendasPorPagamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.codigoBarras,
            p.descricao,
            SUM(i.quantidade),
            SUM(i.precoUnitario * i.quantidade)
        )
        FROM ItemVenda i
        JOIN i.venda v
        JOIN i.produto p
        WHERE v.dataVenda BETWEEN :inicio AND :fim
        AND v.statusFiscal <> 'CANCELADA'
        AND v.statusFiscal <> 'ORCAMENTO'
        GROUP BY p.codigoBarras, p.descricao
        ORDER BY SUM(i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> relatorioProdutosMaisVendidos(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);
}
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

    // ==================================================================================
    // SESSÃO 1: CONSULTAS OPERACIONAIS
    // ==================================================================================

    @Query("SELECT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto WHERE v.id = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);

    List<Venda> findByStatusFiscalOrderByDataVendaDesc(StatusFiscal statusFiscal);

    Page<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    // ==================================================================================
    // SESSÃO 2: RELATÓRIOS ANALÍTICOS
    // ==================================================================================

    // 1. Somatório Geral
    @Query("SELECT COALESCE(SUM(v.totalVenda - v.descontoTotal), 0) FROM Venda v " +
            "WHERE v.dataVenda BETWEEN :inicio AND :fim " +
            "AND v.statusFiscal IN (br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.PENDENTE, br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CONCLUIDA)")
    BigDecimal somarFaturamentoNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 2. Gráfico: Evolução Diária (Ajustado para enviar 3 argumentos)
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO(
            CAST(v.dataVenda AS LocalDate), 
            SUM(v.totalVenda - v.descontoTotal),
            COUNT(v) 
        )
        FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusFiscal IN (br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.PENDENTE, br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CONCLUIDA)
        GROUP BY CAST(v.dataVenda AS LocalDate)
        ORDER BY CAST(v.dataVenda AS LocalDate) ASC
    """)
    List<VendaDiariaDTO> agruparVendasPorDia(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 3. Gráfico: Vendas por Forma de Pagamento
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO(
            v.formaPagamento, 
            SUM(v.totalVenda - v.descontoTotal),
            COUNT(v)
        )
        FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusFiscal IN (br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.PENDENTE, br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CONCLUIDA)
        GROUP BY v.formaPagamento
        ORDER BY SUM(v.totalVenda - v.descontoTotal) DESC
    """)
    List<VendaPorPagamentoDTO> agruparPorFormaPagamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 4. Ranking: Produtos Mais Vendidos
    // A query permanece a mesma, o DTO agora é capaz de aceitar o retorno do SUM
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.codigoBarras, 
            p.descricao, 
            SUM(i.quantidade), 
            SUM(i.precoUnitario * i.quantidade)
        ) 
        FROM ItemVenda i 
        JOIN i.produto p 
        JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusFiscal IN (br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.PENDENTE, br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CONCLUIDA)
        GROUP BY p.codigoBarras, p.descricao 
        ORDER BY SUM(i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingProdutos(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);
}
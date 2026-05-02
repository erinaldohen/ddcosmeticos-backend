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
    // 1. CONSULTAS OPERACIONAIS & DASHBOARD
    // =========================================================================

    @Query("SELECT DISTINCT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto WHERE v.idVenda = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);

    @Query("SELECT v FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA ORDER BY v.dataVenda DESC")
    Page<Venda> buscarVendasPorPeriodoPaginado(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    @Modifying
    @Query("UPDATE Venda v SET v.statusNfce = :novoStatus, v.motivoDoCancelamento = :motivo WHERE v.idVenda = :id")
    void atualizarStatusVenda(@Param("id") Long id, @Param("novoStatus") StatusFiscal novoStatus, @Param("motivo") String motivo);

    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA")
    Long contarVendasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // =========================================================================
    // 2. MÉTRICAS FINANCEIRAS (FINANCEIRO & BI)
    // =========================================================================

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA")
    BigDecimal somarFaturamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    BigDecimal calcularFaturamentoBruto(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

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

    default BigDecimal sumTotalVendaByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim) {
        return somarFaturamento(inicio, fim);
    }

    // =========================================================================
    // 3. RANKINGS E CROSS-SELLING (INTELIGÊNCIA DE VENDAS)
    // =========================================================================

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            COALESCE(p.categoria, 'Diversos'), 
            CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal), 
            CAST(SUM(i.quantidade) AS Long),
            'UN'
        ) 
        FROM ItemVenda i JOIN i.produto p JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        GROUP BY p.categoria
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingCategorias(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            COALESCE(p.marca, 'Diversos'), 
            CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal), 
            CAST(SUM(i.quantidade) AS Long),
            'UN'
        ) 
        FROM ItemVenda i JOIN i.produto p JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        GROUP BY p.marca
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingMarcas(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.descricao, 
            CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal), 
            CAST(SUM(i.quantidade) AS Long),
            'UN'
        ) 
        FROM ItemVenda i JOIN i.produto p JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        GROUP BY p.descricao
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingProdutos(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

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

    @Query(value = """
        SELECT p2.descricao 
        FROM tb_item_venda i1
        JOIN tb_item_venda i2 ON i1.venda_id = i2.venda_id AND i1.produto_id <> i2.produto_id
        JOIN tb_produto p2 ON i2.produto_id = p2.id
        WHERE i1.produto_id = :produtoId
        GROUP BY p2.descricao ORDER BY COUNT(*) DESC LIMIT 2
        """, nativeQuery = true)
    List<String> buscarSugestoesParaProduto(@Param("produtoId") Long produtoId);

    // =========================================================================
    // 4. CANAIS E ORIGENS (BI)
    // =========================================================================

    interface OrigemVendaProjection {
        br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.CanalOrigem getCanalOrigem();
        BigDecimal getTotal();
    }

    @Query("SELECT v.canalOrigem as canalOrigem, COALESCE(SUM(v.valorTotal), 0) as total " +
            "FROM Venda v WHERE v.dataVenda >= :inicioMes " +
            "AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA " +
            "GROUP BY v.canalOrigem ORDER BY total DESC")
    List<OrigemVendaProjection> somarFaturamentoPorOrigemMes(@Param("inicioMes") LocalDateTime inicioMes);

    // =========================================================================
    // 5. INTELIGÊNCIA DE CLIENTE E CRM (RECORRÊNCIA)
    // =========================================================================

    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda >= :inicioMes AND (v.clienteDocumento IS NOT NULL OR v.clienteTelefone IS NOT NULL)")
    Long contarVendasIdentificadasNoMes(@Param("inicioMes") LocalDateTime inicioMes);

    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda >= :inicioMes AND " +
            "((v.clienteDocumento IS NOT NULL AND v.clienteDocumento IN (SELECT v2.clienteDocumento FROM Venda v2 WHERE v2.dataVenda < :inicioMes AND v2.clienteDocumento IS NOT NULL)) OR " +
            "(v.clienteTelefone IS NOT NULL AND v.clienteTelefone IN (SELECT v3.clienteTelefone FROM Venda v3 WHERE v3.dataVenda < :inicioMes AND v3.clienteTelefone IS NOT NULL)))")
    Long contarVendasRecorrentesNoMes(@Param("inicioMes") LocalDateTime inicioMes);

    @Query("SELECT v.idVenda, v.dataVenda, v.valorTotal FROM Venda v WHERE (v.clienteTelefone IS NOT NULL AND v.clienteTelefone != '') OR (v.clienteDocumento IS NOT NULL AND v.clienteDocumento != '')")
    Page<Object[]> buscarTodasVendasIdentificadasPaginadas(Pageable pageable);

    // =========================================================================
    // 6. ROI DA INTELIGÊNCIA ARTIFICIAL
    // =========================================================================

    @Query("""
        SELECT COALESCE(SUM(i.precoUnitario * i.quantidade), 0)
        FROM ItemVenda i
        WHERE i.venda.dataVenda BETWEEN :inicio AND :fim
        AND i.influenciaIA IN ('DIRETA', 'INDIRETA')
        AND i.venda.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
    """)
    BigDecimal calcularFaturamentoInfluenciaIA(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
        SELECT COUNT(i)
        FROM ItemVenda i
        WHERE i.venda.dataVenda BETWEEN :inicio AND :fim
        AND i.influenciaIA IN ('DIRETA', 'INDIRETA')
        AND i.venda.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
    """)
    Long contarItensInfluenciaIA(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // =========================================================================
    // 7. MÉTODOS DE CONVENÇÃO E AUXILIARES (GRÁFICOS E COMISSÕES)
    // =========================================================================

    Page<Venda> findByStatusNfce(StatusFiscal statusNfce, Pageable pageable);
    List<Venda> findByStatusNfceIn(List<StatusFiscal> status);
    Page<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);
    long countByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO(
            DATE(v.dataVenda), 
            CAST(SUM(v.valorTotal) AS BigDecimal),
            CAST(COUNT(v) AS Long)
        )
        FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusNfce <> br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal.CANCELADA
        GROUP BY DATE(v.dataVenda)
        ORDER BY DATE(v.dataVenda) ASC
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

    // --- NOVOS MÉTODOS PARA O MOTOR DE COMISSÕES ---

    @Query("SELECT v FROM Venda v WHERE v.dataVenda >= :inicio AND v.dataVenda <= :fim AND v.statusNfce = :statusNfce")
    Page<Venda> findByDataVendaBetweenAndStatusNfcePaginado(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, @Param("statusNfce") StatusFiscal statusNfce, Pageable pageable);

    @Query("SELECT v FROM Venda v WHERE v.dataVenda >= :inicio AND v.dataVenda <= :fim AND v.usuarioId = :usuarioId AND v.statusNfce = :statusNfce")
    Page<Venda> findByDataVendaBetweenAndUsuarioIdAndStatusNfcePaginado(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, @Param("usuarioId") Long usuarioId, @Param("statusNfce") StatusFiscal statusNfce, Pageable pageable);

    @Query("SELECT v FROM Venda v WHERE v.dataVenda >= :inicio AND v.dataVenda <= :fim AND v.statusNfce = 'AUTORIZADA'")
    Page<Venda> findVendasAutorizadasNoPeriodoPaginado(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    Page<Venda> findByClienteIdOrderByDataVendaDesc(Long clienteId, Pageable pageable);
}
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

    // --- CONTAGENS E BUSCAS SIMPLES ---

    long countByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);

    // Busca por status (Usado na fila de espera)
    List<Venda> findByStatusNfce(StatusFiscal statusNfce);

    List<Venda> findByStatusNfceOrderByDataVendaDesc(StatusFiscal statusNfce);

    // --- MÉTODOS DE HISTÓRICO E LISTAGEM ---

    // 1. Método PAGINADO (Tela de Histórico de Vendas)
    Page<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    // 2. Método LISTA COMPLETA (Dashboard e Cálculos)
    List<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);

    // 3. Busca OTIMIZADA para Detalhes (Evita LazyInitializationException)
    // Carrega Venda + Itens + Pagamentos em uma única query
    @Query("SELECT DISTINCT v FROM Venda v " +
            "LEFT JOIN FETCH v.itens i " +
            "LEFT JOIN FETCH i.produto " +
            "LEFT JOIN FETCH v.pagamentos p " +
            "WHERE v.idVenda = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);

    // --- MÉTODOS DE CAIXA E TOTAIS ---

    @Query("SELECT v FROM Venda v WHERE v.usuario.id = :usuarioId AND v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce != 'CANCELADA'")
    List<Venda> buscarVendasDoUsuarioNoPeriodo(@Param("usuarioId") Long usuarioId,
                                               @Param("inicio") LocalDateTime inicio,
                                               @Param("fim") LocalDateTime fim);

    // Soma total bruta (exceto canceladas)
    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce != 'CANCELADA'")
    BigDecimal sumTotalVendaByDataVendaBetween(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // --- RELATÓRIOS E DTOs ---

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce != 'CANCELADA'")
    BigDecimal somarFaturamentoNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /*
     * Vendas por Dia (Gráfico de Linha)
     * Nota: O ideal é agrupar por CAST(v.dataVenda as DATE), mas JPQL puro varia por banco.
     * Esta query retorna os dados, o Service pode agrupar os dias se o gráfico ficar muito granular.
     */
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO(
            v.dataVenda, 
            SUM(v.valorTotal),
            COUNT(v) 
        )
        FROM Venda v 
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusNfce != 'CANCELADA' 
        GROUP BY v.dataVenda
        ORDER BY v.dataVenda ASC
    """)
    List<VendaDiariaDTO> agruparVendasPorDia(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /*
     * CORREÇÃO IMPORTANTE: Agrupamento por Pagamento (Gráfico de Pizza)
     * Agora consulta a tabela 'PagamentoVenda' (p) e não a 'Venda' (v) para somar corretamente
     * vendas com múltiplos meios de pagamento (Split Payment).
     */
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO(
            p.formaPagamento, 
            SUM(p.valor),
            COUNT(DISTINCT v)
        )
        FROM PagamentoVenda p 
        JOIN p.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusNfce != 'CANCELADA'
        GROUP BY p.formaPagamento
    """)
    List<VendaPorPagamentoDTO> agruparPorFormaPagamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /*
     * Ranking de Produtos/Marcas (Curva ABC)
     */
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO(
            p.marca, 
            SUM(i.precoUnitario * i.quantidade), 
            COUNT(DISTINCT v)
        ) 
        FROM ItemVenda i 
        JOIN i.produto p 
        JOIN i.venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim 
        AND v.statusNfce != 'CANCELADA'
        GROUP BY p.marca 
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<ProdutoRankingDTO> buscarRankingMarcas(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);
    // --- NOVO MÉTODO BLINDADO PARA ATUALIZAR STATUS ---
    @Modifying
    @Query("UPDATE Venda v SET v.statusNfce = :novoStatus, v.motivoDoCancelamento = :motivo WHERE v.idVenda = :id")
    void atualizarStatusVenda(@Param("id") Long id, @Param("novoStatus") StatusFiscal novoStatus, @Param("motivo") String motivo);

    // CORREÇÃO: Carrega apenas ITENS (1ª Lista) para evitar MultipleBagFetchException
    @Query("SELECT DISTINCT v FROM Venda v " +
            "LEFT JOIN FETCH v.itens i " +
            "LEFT JOIN FETCH i.produto " +
            "WHERE v.idVenda = :id")
    Optional<Venda> findByIdWithItens(@Param("id") Long id);
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import jakarta.persistence.Tuple;
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

    @Query("SELECT v FROM Venda v LEFT JOIN FETCH v.itens WHERE v.dataVenda BETWEEN :inicio AND :fim")
    List<Venda> buscarPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT SUM(v.totalVenda) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    BigDecimal somarVendasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    Long contarVendasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    List<Venda> findByStatusFiscalOrderByDataVendaDesc(StatusFiscal status);

    // --- QUERIES DE RELATÓRIO (USANDO TUPLE PARA EVITAR ERROS) ---

    @Query("""
        SELECT 
            cast(v.dataVenda as LocalDate) as data,
            SUM(v.totalVenda) as total,
            COUNT(v) as qtd
        FROM Venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim
        AND v.statusFiscal NOT IN :statusExcluidos
        GROUP BY cast(v.dataVenda as LocalDate)
        ORDER BY cast(v.dataVenda as LocalDate) ASC
    """)
    List<Tuple> relatorioVendasPorDia(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("statusExcluidos") List<StatusFiscal> statusExcluidos
    );

    @Query("""
        SELECT 
            v.formaPagamento as forma,
            SUM(v.totalVenda) as total,
            COUNT(v) as qtd
        FROM Venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim
        AND v.statusFiscal NOT IN :statusExcluidos
        GROUP BY v.formaPagamento
    """)
    List<Tuple> relatorioVendasPorPagamento(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("statusExcluidos") List<StatusFiscal> statusExcluidos
    );

    @Query("""
        SELECT 
            p.codigoBarras as codigo,
            p.descricao as nome,
            SUM(i.quantidade) as qtd,
            SUM(i.precoUnitario * i.quantidade) as total
        FROM ItemVenda i
        JOIN i.venda v
        JOIN i.produto p
        WHERE v.dataVenda BETWEEN :inicio AND :fim
        AND v.statusFiscal NOT IN :statusExcluidos
        GROUP BY p.codigoBarras, p.descricao
        ORDER BY SUM(i.quantidade) DESC
    """)
    List<Tuple> relatorioProdutosMaisVendidos(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("statusExcluidos") List<StatusFiscal> statusExcluidos,
            Pageable pageable
    );
}
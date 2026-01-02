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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Long> {

    @Query("SELECT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto WHERE v.id = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);

    // Listagem Geral de Vendas
    Page<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    // Vendas Suspensas
    List<Venda> findByStatusFiscalOrderByDataVendaDesc(StatusFiscal status);

    // Query unificada para Relatórios e Dashboard (Performance com Tuple)
    @Query("""
        SELECT 
            CAST(v.dataVenda AS date) as data,
            SUM(v.totalVenda - v.descontoTotal) as total,
            COUNT(v) as qtd
        FROM Venda v
        WHERE v.dataVenda BETWEEN :inicio AND :fim
        AND v.statusFiscal NOT IN :statusExcluidos
        GROUP BY CAST(v.dataVenda AS date)
        ORDER BY CAST(v.dataVenda AS date) ASC
    """)
    List<Tuple> relatorioVendasPorDia(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("statusExcluidos") List<StatusFiscal> statusExcluidos
    );

    @Query("""
        SELECT 
            v.formaPagamento as forma,
            SUM(v.totalVenda - v.descontoTotal) as total,
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

    @Query("""
    SELECT 
        SUM(i.precoUnitario * i.quantidade) as faturamento,
        SUM(i.custoUnitarioHistorico * i.quantidade) as custoTotal,
        SUM((i.precoUnitario - i.custoUnitarioHistorico) * i.quantidade) as lucroBruto
    FROM ItemVenda i JOIN i.venda v
    WHERE v.dataVenda BETWEEN :inicio AND :fim
    AND v.statusFiscal NOT IN :statusExcluidos
""")
    Tuple resumoLucratividade(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, @Param("statusExcluidos") List<StatusFiscal> statusExcluidos);
}
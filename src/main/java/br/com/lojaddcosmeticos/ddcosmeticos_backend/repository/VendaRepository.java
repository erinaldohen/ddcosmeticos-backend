package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal; // Importante para o SUM

@Repository
public interface VendaRepository extends JpaRepository<Venda, Long> {

    // 1. Busca otimizada (JOIN FETCH) para evitar LazyInitializationException
    // Traz a venda E os itens numa única ida ao banco
    @Query("SELECT v FROM Venda v JOIN FETCH v.itens WHERE v.id = :id")
    Optional<Venda> findByIdWithItens(@Param("id") Long id);

    // 2. Relatório por Período
    // JOIN FETCH aqui também é crucial para performance do relatório financeiro
    @Query("SELECT DISTINCT v FROM Venda v JOIN FETCH v.itens WHERE v.dataVenda BETWEEN :inicio AND :fim")
    List<Venda> buscarPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 3. Somatórios para o Dashboard (Consultas Rápidas)
    @Query("SELECT COALESCE(SUM(v.totalVenda), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    BigDecimal somarVendasPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    Long contarVendasPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /**
     * Busca a venda carregando os itens imediatamente (EAGER) via JOIN FETCH.
     * Isso evita o erro 'LazyInitializationException' no Controller ou na NFC-e.
     */
    @Query("SELECT v FROM Venda v JOIN FETCH v.itens WHERE v.id = :id")
    Optional<Venda> findByIdComItens(@Param("id") Long id);
}
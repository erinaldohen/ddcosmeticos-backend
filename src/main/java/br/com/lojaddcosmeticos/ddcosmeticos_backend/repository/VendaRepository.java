package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
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

    // Método para carregar itens (que adicionamos antes)
    @Query("SELECT v FROM Venda v JOIN FETCH v.itens WHERE v.id = :id")
    Optional<Venda> findByIdWithItens(@Param("id") Long id);

    // --- ADICIONE ESTE MÉTODO ---
    // O Spring cria automaticamente o SQL: WHERE data_venda BETWEEN ? AND ?
    List<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);

    @Query("SELECT COALESCE(SUM(v.totalVenda), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    BigDecimal somarVendasPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("SELECT COUNT(v) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim")
    Long contarVendasPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // Busca vendas dentro de um intervalo de datas (ex: 00:00 até 23:59)
    @Query("SELECT v FROM Venda v JOIN FETCH v.itens WHERE v.dataVenda BETWEEN :inicio AND :fim")
    List<Venda> buscarPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
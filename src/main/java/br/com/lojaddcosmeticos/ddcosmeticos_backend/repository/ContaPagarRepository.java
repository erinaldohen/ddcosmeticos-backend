package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoDespesaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long> {

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento = :data AND c.status = 'PENDENTE'")
    BigDecimal somarAPagarPorData(@Param("data") LocalDate data);

    // OTIMIZAÇÃO: Consulta por período
    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento BETWEEN :inicio AND :fim AND c.status = 'PENDENTE'")
    BigDecimal somarPagamentosNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoDespesaDTO(c.categoria, COUNT(c), SUM(c.valorTotal)) " +
            "FROM ContaPagar c WHERE c.dataVencimento BETWEEN :inicio AND :fim GROUP BY c.categoria")
    List<ResumoDespesaDTO> agruparDespesasPorPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    List<ContaPagar> findByDataVencimentoBeforeAndStatus(LocalDate data, String status);
}
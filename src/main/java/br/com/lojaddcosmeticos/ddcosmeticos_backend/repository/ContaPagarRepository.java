package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoDespesaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta; // Import Essencial
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

    // Soma o total que vence hoje e ainda está pendente
    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento = :data AND c.status = 'PENDENTE'")
    BigDecimal somarAPagarPorData(@Param("data") LocalDate data);

    // Soma o total que vence num período (usado para fluxo de caixa / projeção)
    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento BETWEEN :inicio AND :fim AND c.status = 'PENDENTE'")
    BigDecimal somarPagamentosNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    // [NOVO] Soma todas as contas atrasadas (Vencimento anterior a hoje e Pendente)
    // Esse é o método que o DashboardService está reclamando que falta!
    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento < :hoje AND c.status = 'PENDENTE'")
    BigDecimal somarTotalVencido(@Param("hoje") LocalDate hoje);

    // Agrupamento para gráficos
    @Query("SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoDespesaDTO(c.categoria, COUNT(c), SUM(c.valorTotal)) " +
            "FROM ContaPagar c WHERE c.dataVencimento BETWEEN :inicio AND :fim GROUP BY c.categoria")
    List<ResumoDespesaDTO> agruparDespesasPorPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    // [CORREÇÃO] Alterado de String para StatusConta para não quebrar o contexto do Spring nos testes
    List<ContaPagar> findByDataVencimentoBeforeAndStatus(LocalDate data, StatusConta status);
}
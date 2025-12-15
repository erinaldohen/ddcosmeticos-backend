package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

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

    // Busca contas pendentes (útil para o dashboard financeiro)
    List<ContaPagar> findByStatus(ContaPagar.StatusConta status);

    // Busca histórico de contas de um fornecedor
    List<ContaPagar> findByFornecedorId(Long idFornecedor);

    // Soma contas PENDENTES para uma data específica
    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento = :data AND c.status = 'PENDENTE'")
    BigDecimal somarAPagarPorData(@Param("data") LocalDate data);

    // Soma contas ATRASADAS (Vencimento < Hoje e Status Pendente)
    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento < :hoje AND c.status = 'PENDENTE'")
    BigDecimal somarTotalAtrasado(@Param("hoje") LocalDate hoje);
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoDespesaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long> {

    // ==================================================================================
    // SEÇÃO 1: CONSULTAS DE FLUXO E FECHAMENTO (AGORA PAGINADAS PARA EVITAR MEMORY LEAK)
    // ==================================================================================

    Page<ContaPagar> findByDataPagamentoAndStatus(LocalDate dataPagamento, StatusConta status, Pageable pageable);

    Page<ContaPagar> findByDataVencimentoBetween(LocalDate inicio, LocalDate fim, Pageable pageable);

    Page<ContaPagar> findByStatus(StatusConta status, Pageable pageable);

    Page<ContaPagar> findByDataVencimentoBeforeAndStatus(LocalDate data, StatusConta status, Pageable pageable);

    // ==================================================================================
    // SEÇÃO 2: AGREGAÇÕES PARA BI E DASHBOARD (Inteligência Matemática mantida)
    // ==================================================================================

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento < :hoje AND c.status <> 'PAGA'")
    BigDecimal somarTotalVencido(@Param("hoje") LocalDate hoje);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento = :data AND c.status = :status")
    BigDecimal somarValorPorVencimentoEStatus(@Param("data") LocalDate data, @Param("status") StatusConta status);

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoDespesaDTO(
            COALESCE(f.nomeFantasia, 'Despesa Avulsa'), 
            COUNT(c), 
            SUM(c.valorTotal)
        ) 
        FROM ContaPagar c 
        LEFT JOIN c.fornecedor f
        WHERE c.dataVencimento BETWEEN :inicio AND :fim 
        GROUP BY f.nomeFantasia
    """)
    List<ResumoDespesaDTO> agruparDespesasPorPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    // ==================================================================================
    // SEÇÃO 3: AUXILIARES DE GESTÃO E ROTINAS CIRÚRGICAS
    // ==================================================================================

    @Query("SELECT c FROM ContaPagar c WHERE c.dataVencimento < :hoje AND c.status <> 'PAGA'")
    List<ContaPagar> buscarContasVencidas(@Param("hoje") LocalDate hoje);

    @Query("SELECT c FROM ContaPagar c WHERE c.status = 'PENDENTE' ORDER BY c.dataVencimento ASC")
    Page<ContaPagar> buscarProximosVencimentos(Pageable pageable);

    // Busca cirúrgica, pode continuar List (são poucas contas por fornecedor)
    List<ContaPagar> findByFornecedorId(Long fornecedorId);
}
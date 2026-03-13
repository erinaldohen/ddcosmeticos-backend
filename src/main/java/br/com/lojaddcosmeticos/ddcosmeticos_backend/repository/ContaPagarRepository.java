package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoDespesaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
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

    // ==================================================================================
    // SEÇÃO 1: CONSULTAS DE FLUXO E FECHAMENTO
    // ==================================================================================

    List<ContaPagar> findByDataPagamentoAndStatus(LocalDate dataPagamento, StatusConta status);

    List<ContaPagar> findByDataVencimentoBetween(LocalDate inicio, LocalDate fim);

    List<ContaPagar> findByStatus(StatusConta status);

    List<ContaPagar> findByDataVencimentoBeforeAndStatus(LocalDate data, StatusConta status);

    // ==================================================================================
    // SEÇÃO 2: AGREGAÇÕES PARA BI E DASHBOARD (Inteligência)
    // ==================================================================================

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento < :hoje AND c.status <> 'PAGA'")
    BigDecimal somarTotalVencido(@Param("hoje") LocalDate hoje);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaPagar c WHERE c.dataVencimento = :data AND c.status = :status")
    BigDecimal somarValorPorVencimentoEStatus(@Param("data") LocalDate data, @Param("status") StatusConta status);

    // CORREÇÃO CRÍTICA: Removido o campo 'categoria' que não existe.
    // O Fallback 'Despesa Avulsa' agora é a única alternativa se não houver fornecedor.
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
    // SEÇÃO 3: AUXILIARES DE GESTÃO
    // ==================================================================================

    @Query("SELECT c FROM ContaPagar c WHERE c.dataVencimento < :hoje AND c.status <> 'PAGA'")
    List<ContaPagar> buscarContasVencidas(@Param("hoje") LocalDate hoje);

    @Query("SELECT c FROM ContaPagar c WHERE c.status = 'PENDENTE' ORDER BY c.dataVencimento ASC")
    List<ContaPagar> buscarProximosVencimentos();

    List<ContaPagar> findByFornecedorId(Long fornecedorId);
}
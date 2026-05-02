package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
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
public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long> {

    // ==================================================================================
    // SEÇÃO 1: CONSULTAS OPERACIONAIS E PDV (CRÍTICAS)
    // ==================================================================================

    // Cirúrgica (uma única venda) = List é seguro.
    @Query("SELECT c FROM ContaReceber c JOIN FETCH c.cliente WHERE c.venda = :venda")
    List<ContaReceber> findByVenda(@Param("venda") Venda venda);

    @Query("SELECT c FROM ContaReceber c WHERE c.venda.idVenda = :vendaId")
    List<ContaReceber> findByVendaId(@Param("vendaId") Long vendaId);

    // Relatórios de fechamento (Potencialmente grandes) = Page é obrigatório.
    Page<ContaReceber> findByDataPagamentoAndStatus(LocalDate dataPagamento, StatusConta status, Pageable pageable);

    Page<ContaReceber> findByStatus(StatusConta status, Pageable pageable);

    // ==================================================================================
    // SEÇÃO 2: MOTOR DE CRÉDITO E CRM (Comportamento Excelente Mantido)
    // ==================================================================================

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c " +
            "WHERE c.cliente.documento = :documento " +
            "AND c.status <> 'PAGA'")
    BigDecimal somarDividaTotalPorDocumento(@Param("documento") String documento);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ContaReceber c " +
            "WHERE c.cliente.documento = :documento " +
            "AND c.status <> 'PAGA' " +
            "AND c.dataVencimento < :hoje")
    boolean existeContaVencida(@Param("documento") String documento, @Param("hoje") LocalDate hoje);

    // Lista Cirúrgica para o extrato de UM cliente
    @Query("SELECT c FROM ContaReceber c " +
            "WHERE c.cliente.documento = :documento " +
            "AND c.status <> 'PAGA' " +
            "ORDER BY c.dataVencimento ASC")
    List<ContaReceber> listarContasEmAberto(@Param("documento") String documento);

    // ==================================================================================
    // SEÇÃO 3: INTELIGÊNCIA DE DASHBOARD E PROJEÇÕES
    // ==================================================================================

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c " +
            "WHERE c.dataVencimento BETWEEN :inicio AND :fim " +
            "AND c.status <> 'PAGA'")
    BigDecimal somarRecebiveisNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c " +
            "WHERE c.dataVencimento < :hoje " +
            "AND c.status <> 'PAGA'")
    BigDecimal somarTotalInadimplencia(@Param("hoje") LocalDate hoje);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c WHERE c.dataVencimento = :data")
    BigDecimal sumValorByDataVencimento(@Param("data") LocalDate data);
}
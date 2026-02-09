package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
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
    // SESSÃO 1: MÉTODOS BÁSICOS
    // ==================================================================================

    List<ContaReceber> findByVenda(Venda venda);

    // [CORREÇÃO AQUI]
    // O erro ocorria porque o Spring procurava 'venda.id', mas sua classe tem 'venda.idVenda'.
    // Usamos @Query para forçar o caminho correto.
    @Query("SELECT c FROM ContaReceber c WHERE c.venda.idVenda = :vendaId")
    List<ContaReceber> findByVendaId(@Param("vendaId") Long vendaId);

    List<ContaReceber> findByDataPagamentoAndStatus(LocalDate dataPagamento, StatusConta status);

    List<ContaReceber> findByStatus(StatusConta status);

    // ==================================================================================
    // SESSÃO 2: VALIDAÇÃO DE CRÉDITO
    // ==================================================================================

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c " +
            "WHERE c.cliente.documento = :documento " +
            "AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE")
    BigDecimal somarDividaTotalPorDocumento(@Param("documento") String documento);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ContaReceber c " +
            "WHERE c.cliente.documento = :documento " +
            "AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE " +
            "AND c.dataVencimento < :hoje")
    boolean existeContaVencida(@Param("documento") String documento, @Param("hoje") LocalDate hoje);

    // ==================================================================================
    // SESSÃO 3: RELATÓRIOS E OPERACIONAL
    // ==================================================================================

    @Query("SELECT DISTINCT c.cliente.documento " +
            "FROM ContaReceber c " +
            "WHERE c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE")
    List<String> buscarDocumentosComPendencia();

    @Query("SELECT c FROM ContaReceber c " +
            "WHERE c.cliente.documento = :documento " +
            "AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE " +
            "ORDER BY c.dataVencimento ASC")
    List<ContaReceber> listarContasEmAberto(@Param("documento") String documento);

    // ==================================================================================
    // SESSÃO 4: DASHBOARD FINANCEIRO
    // ==================================================================================

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c WHERE c.dataVencimento = :data")
    BigDecimal sumValorByDataVencimento(@Param("data") LocalDate data);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c WHERE c.dataVencimento BETWEEN :inicio AND :fim")
    BigDecimal somarRecebiveisNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
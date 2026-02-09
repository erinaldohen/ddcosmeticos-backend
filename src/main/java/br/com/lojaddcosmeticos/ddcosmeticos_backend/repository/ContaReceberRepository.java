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

    // DBA: Query otimizada. Evita N+1 trazendo o cliente junto se necessário.
    @Query("SELECT c FROM ContaReceber c JOIN FETCH c.cliente WHERE c.venda = :venda")
    List<ContaReceber> findByVenda(@Param("venda") Venda venda);

    // Backend Fix: Resolve o erro "No property id found for type Venda"
    // Acessamos c.venda.idVenda explicitamente
    @Query("SELECT c FROM ContaReceber c WHERE c.venda.idVenda = :vendaId")
    List<ContaReceber> findByVendaId(@Param("vendaId") Long vendaId);

    List<ContaReceber> findByDataPagamentoAndStatus(LocalDate dataPagamento, StatusConta status);

    List<ContaReceber> findByStatus(StatusConta status);

    // ==================================================================================
    // VALIDAÇÃO DE CRÉDITO
    // ==================================================================================

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c " +
            "WHERE c.cliente.documento = :documento " +
            "AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE")
    BigDecimal somarDividaTotalPorDocumento(@Param("documento") String documento);

    // DBA: Uso de EXISTS (CASE WHEN COUNT > 0) é muito mais rápido que trazer os objetos
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ContaReceber c " +
            "WHERE c.cliente.documento = :documento " +
            "AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE " +
            "AND c.dataVencimento < :hoje")
    boolean existeContaVencida(@Param("documento") String documento, @Param("hoje") LocalDate hoje);

    // ==================================================================================
    // RELATÓRIOS E DASHBOARD
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

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c WHERE c.dataVencimento = :data")
    BigDecimal sumValorByDataVencimento(@Param("data") LocalDate data);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c WHERE c.dataVencimento BETWEEN :inicio AND :fim")
    BigDecimal somarRecebiveisNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long> {

    // --- MÉTODOS BÁSICOS ---
    List<ContaReceber> findByIdVendaRef(Long idVendaRef);
    List<ContaReceber> findByStatus(StatusConta status);

    // [NOVO] Método essencial para o Fechamento de Caixa Otimizado (FinanceiroService)
    List<ContaReceber> findByDataPagamentoAndStatus(LocalDate dataPagamento, StatusConta status);

    // ==================================================================================
    // SESSÃO 2: VALIDAÇÃO DE CRÉDITO (Lógica complexa de risco de cliente)
    // ==================================================================================

    @Query("""
        SELECT COALESCE(SUM(c.valorLiquido), 0)
        FROM ContaReceber c 
        WHERE c.idVendaRef IN (SELECT v.id FROM Venda v WHERE v.clienteDocumento = :documento)
        AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
    """)
    BigDecimal somarDividaTotalPorDocumento(@Param("documento") String documento);

    @Query("""
        SELECT COUNT(c) > 0 
        FROM ContaReceber c 
        WHERE c.idVendaRef IN (SELECT v.id FROM Venda v WHERE v.clienteDocumento = :documento)
        AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
        AND c.dataVencimento < :hoje
    """)
    boolean existeContaVencida(@Param("documento") String documento, @Param("hoje") LocalDate hoje);

    // ==================================================================================
    // SESSÃO 3: RELATÓRIOS E OPERACIONAL
    // ==================================================================================

    @Query("""
        SELECT DISTINCT v.clienteDocumento 
        FROM ContaReceber c 
        JOIN Venda v ON c.idVendaRef = v.id 
        WHERE c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
    """)
    List<String> buscarDocumentosComPendencia();

    @Query("""
        SELECT c 
        FROM ContaReceber c 
        WHERE c.idVendaRef IN (SELECT v.id FROM Venda v WHERE v.clienteDocumento = :documento)
        AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
        ORDER BY c.dataVencimento ASC
    """)
    List<ContaReceber> listarContasEmAberto(@Param("documento") String documento);

    // SESSÃO 4: DASHBOARD FINANCEIRO
    @Query("SELECT COALESCE(SUM(c.valorLiquido), 0) FROM ContaReceber c WHERE c.dataVencimento BETWEEN :inicio AND :fim")
    BigDecimal somarRecebiveisNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
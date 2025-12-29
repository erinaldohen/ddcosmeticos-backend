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

    // ==================================================================================
    // SESSÃO 1: MÉTODOS LEGADOS E OPERACIONAIS
    // ==================================================================================
    List<ContaReceber> findByIdVendaRef(Long idVendaRef);
    List<ContaReceber> findByStatus(StatusConta status);

    // ==================================================================================
    // SESSÃO 2: VALIDAÇÃO DE CRÉDITO (MÓDULO FIADO)
    // ==================================================================================

    // CORREÇÃO: v.clienteCpf -> v.clienteDocumento
    @Query("""
        SELECT SUM(c.valorLiquido) 
        FROM ContaReceber c 
        WHERE c.idVendaRef IN (SELECT v.id FROM Venda v WHERE v.clienteDocumento = :cpf)
        AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
    """)
    BigDecimal somarDividaTotalPorCpf(@Param("cpf") String cpf);

    // CORREÇÃO: v.clienteCpf -> v.clienteDocumento
    @Query("""
        SELECT COUNT(c) > 0 
        FROM ContaReceber c 
        WHERE c.idVendaRef IN (SELECT v.id FROM Venda v WHERE v.clienteDocumento = :cpf)
        AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
        AND c.dataVencimento < :hoje
    """)
    boolean existeContaVencida(@Param("cpf") String cpf, @Param("hoje") LocalDate hoje);

    // ==================================================================================
    // SESSÃO 3: RELATÓRIOS DE INADIMPLÊNCIA
    // ==================================================================================

    // CORREÇÃO: v.clienteCpf -> v.clienteDocumento
    @Query("""
        SELECT DISTINCT v.clienteDocumento 
        FROM ContaReceber c 
        JOIN Venda v ON c.idVendaRef = v.id 
        WHERE c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
    """)
    List<String> buscarCpfsComPendencia();

    // CORREÇÃO: v.clienteCpf -> v.clienteDocumento
    @Query("""
        SELECT c 
        FROM ContaReceber c 
        WHERE c.idVendaRef IN (SELECT v.id FROM Venda v WHERE v.clienteDocumento = :cpf)
        AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
        ORDER BY c.dataVencimento ASC
    """)
    List<ContaReceber> listarContasEmAberto(@Param("cpf") String cpf);

    // ==================================================================================
    // SESSÃO 4: DASHBOARD
    // ==================================================================================

    @Query("SELECT SUM(c.valorLiquido) FROM ContaReceber c WHERE c.dataVencimento BETWEEN :inicio AND :fim")
    BigDecimal somarRecebiveisNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
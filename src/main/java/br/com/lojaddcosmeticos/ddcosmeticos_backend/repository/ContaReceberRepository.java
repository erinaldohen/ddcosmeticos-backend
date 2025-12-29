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

    /**
     * Busca contas de uma venda específica.
     * Usado principalmente no cancelamento/estorno de vendas.
     */
    List<ContaReceber> findByIdVendaRef(Long idVendaRef);

    /**
     * Busca por status genérico.
     */
    List<ContaReceber> findByStatus(StatusConta status);

    // ==================================================================================
    // SESSÃO 2: VALIDAÇÃO DE CRÉDITO (USADO NO VENDA SERVICE)
    // ==================================================================================

    /**
     * Calcula o total da dívida ATIVA (Pendente) de um cliente baseado no CPF.
     * Faz um JOIN implícito com a tabela de Vendas para encontrar o CPF.
     */
    @Query("""
        SELECT SUM(c.valorLiquido) 
        FROM ContaReceber c 
        WHERE c.idVendaRef IN (SELECT v.id FROM Venda v WHERE v.clienteCpf = :cpf)
        AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
    """)
    BigDecimal somarDividaTotalPorCpf(@Param("cpf") String cpf);

    /**
     * Verifica se o cliente tem alguma conta vencida (atrasada) e não paga.
     * Retorna TRUE se for caloteiro/atrasado.
     */
    @Query("""
        SELECT COUNT(c) > 0 
        FROM ContaReceber c 
        WHERE c.idVendaRef IN (SELECT v.id FROM Venda v WHERE v.clienteCpf = :cpf)
        AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
        AND c.dataVencimento < :hoje
    """)
    boolean existeContaVencida(@Param("cpf") String cpf, @Param("hoje") LocalDate hoje);

    // ==================================================================================
    // SESSÃO 3: MÉTODOS PARA RELATÓRIOS
    // ==================================================================================

    /**
     * Retorna lista de CPFs distintos que possuem dívidas pendentes.
     * Usado para iterar e gerar o relatório de inadimplência.
     */
    @Query("""
        SELECT DISTINCT v.clienteCpf 
        FROM ContaReceber c 
        JOIN Venda v ON c.idVendaRef = v.id 
        WHERE c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
    """)
    List<String> buscarCpfsComPendencia();

    /**
     * Lista detalhada de todas as parcelas em aberto de um cliente.
     * Usado no relatório detalhado.
     */
    @Query("""
        SELECT c 
        FROM ContaReceber c 
        WHERE c.idVendaRef IN (SELECT v.id FROM Venda v WHERE v.clienteCpf = :cpf)
        AND c.status = br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta.PENDENTE
        ORDER BY c.dataVencimento ASC
    """)
    List<ContaReceber> listarContasEmAberto(@Param("cpf") String cpf);
}
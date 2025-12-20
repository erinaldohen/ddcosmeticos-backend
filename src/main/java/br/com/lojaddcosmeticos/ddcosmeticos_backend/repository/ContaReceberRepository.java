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

    /**
     * RESOLVE O ERRO: Soma o faturamento total em um período.
     * Ignora títulos com status 'CANCELADO'.
     */
    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c " +
            "WHERE c.dataEmissao BETWEEN :inicio AND :fim " +
            "AND c.status <> 'CANCELADO'")
    BigDecimal somarRecebiveisNoPeriodo(@Param("inicio") LocalDate inicio,
                                        @Param("fim") LocalDate fim);

    // RESOLVE O ERRO DA LINHA 34: Busca títulos por data ignorando os cancelados
    List<ContaReceber> findByDataEmissaoAndStatusNot(LocalDate data, StatusConta status);

    // Método para o saldo projetado (usado no FinanceiroService)
    @Query("SELECT SUM(c.valorTotal) FROM ContaReceber c WHERE c.dataVencimento = :data AND c.status = 'PENDENTE'")
    BigDecimal somarAReceberPorData(@Param("data") LocalDate data);

    // Essencial para o cancelamento funcionar
    List<ContaReceber> findByIdVendaRef(Long vendaId);
}
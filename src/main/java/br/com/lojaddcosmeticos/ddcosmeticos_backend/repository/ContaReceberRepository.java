package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
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
     * RESOLVE O ERRO: Soma o faturamento total em um intervalo de datas.
     * COALESCE garante que retorne 0 em vez de null caso não haja vendas no período.
     */
    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c " +
            "WHERE c.dataEmissao BETWEEN :inicio AND :fim " +
            "AND c.status <> 'CANCELADO'")
    BigDecimal somarRecebiveisNoPeriodo(@Param("inicio") LocalDate inicio,
                                        @Param("fim") LocalDate fim);

    // Outros métodos necessários para o FinanceiroService e RelatorioService
    List<ContaReceber> findByDataEmissaoAndStatusNot(LocalDate data, StatusConta status);
    // No arquivo ContaReceberRepository.java
    List<ContaReceber> findByIdVendaRef(Long vendaId);

}
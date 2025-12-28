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
     * Soma o valor total das contas a receber que VENCEM no período informado.
     * Usado para projeção de fluxo de caixa (O que vai entrar?).
     *
     * @param inicio Data inicial do vencimento
     * @param fim Data final do vencimento
     * @return Soma total ou 0
     */
    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM ContaReceber c " +
            "WHERE c.dataVencimento BETWEEN :inicio AND :fim " +
            "AND c.status <> 'CANCELADO'")
    BigDecimal somarRecebiveisNoPeriodo(@Param("inicio") LocalDate inicio,
                                        @Param("fim") LocalDate fim);

    // Listar contas por data de emissão (ex: Relatório de vendas do dia)
    List<ContaReceber> findByDataEmissaoAndStatusNot(LocalDate dataEmissao, StatusConta status);

    // Buscar contas vinculadas a uma venda específica
    List<ContaReceber> findByIdVendaRef(Long idVendaRef);
}
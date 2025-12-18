package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoPagamentoDTO;
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

    List<ContaReceber> findByIdVendaRef(Long idVenda);

    // Consulta por dia único (usada no Financeiro)
    @Query("SELECT COALESCE(SUM(c.valorLiquido), 0) FROM ContaReceber c WHERE c.dataVencimento = :data AND c.status = 'PENDENTE'")
    BigDecimal somarAReceberPorData(@Param("data") LocalDate data);

    // OTIMIZAÇÃO: Consulta por período (usada no Dashboard)
    @Query("SELECT COALESCE(SUM(c.valorLiquido), 0) FROM ContaReceber c WHERE c.dataVencimento BETWEEN :inicio AND :fim AND c.status = 'PENDENTE'")
    BigDecimal somarRecebiveisNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoPagamentoDTO(" +
            "CAST(c.formaPagamento AS string), COUNT(c), SUM(c.valorTotal)) " +
            "FROM ContaReceber c " +
            "WHERE c.dataEmissao = :data " +
            "GROUP BY c.formaPagamento")
    List<ResumoPagamentoDTO> agruparPagamentosPorData(@Param("data") LocalDate data);
}
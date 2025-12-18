package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoPagamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long> {

    @Query("SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoPagamentoDTO(" +
            "CAST(c.formaPagamento AS string), COUNT(c), SUM(c.valorTotal)) " +
            "FROM ContaReceber c " +
            "WHERE c.dataEmissao = :data " +
            "GROUP BY c.formaPagamento")
    List<ResumoPagamentoDTO> agruparPagamentosPorData(@Param("data") LocalDate data);
}
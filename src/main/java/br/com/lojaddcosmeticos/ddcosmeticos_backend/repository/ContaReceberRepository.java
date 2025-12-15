package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

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

    // Soma recebíveis PENDENTES (ou seja, que vão cair na conta) para uma data
    @Query("SELECT COALESCE(SUM(c.valorLiquido), 0) FROM ContaReceber c WHERE c.dataVencimento = :data AND c.status = 'PENDENTE'")
    BigDecimal somarAReceberPorData(@Param("data") LocalDate data);
}
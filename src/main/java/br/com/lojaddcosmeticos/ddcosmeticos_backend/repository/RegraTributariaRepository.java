package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.RegraTributaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface RegraTributariaRepository extends JpaRepository<RegraTributaria, Long> {

    // ✅ PERFEITO: Retorna Optional (protege contra NullPointerException) e a busca é O(1).
    @Query("SELECT r FROM RegraTributaria r WHERE :data BETWEEN r.dataInicio AND r.dataFim")
    Optional<RegraTributaria> findRegraVigente(@Param("data") LocalDate data);
}
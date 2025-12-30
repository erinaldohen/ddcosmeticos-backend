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

    // Busca a regra onde a data informada esteja dentro do período de vigência
    @Query("SELECT r FROM RegraTributaria r WHERE :data BETWEEN r.dataInicio AND r.dataFim")
    Optional<RegraTributaria> findRegraVigente(@Param("data") LocalDate data);
}
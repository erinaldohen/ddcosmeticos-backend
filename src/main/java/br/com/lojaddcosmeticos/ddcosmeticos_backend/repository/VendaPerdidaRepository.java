package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.VendaPerdida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface VendaPerdidaRepository extends JpaRepository<VendaPerdida, Long> {
    @Query("SELECT COUNT(v) FROM VendaPerdida v WHERE v.dataRegistro BETWEEN :inicio AND :fim")
    Long contarRupturasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
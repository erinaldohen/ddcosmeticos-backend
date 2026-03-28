package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.VendaPerdida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VendaPerdidaRepository extends JpaRepository<VendaPerdida, Long> {

    @Query("SELECT COUNT(v) FROM VendaPerdida v WHERE v.dataRegistro BETWEEN :inicio AND :fim")
    Long contarRupturasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // 🚨 CORRIGIDO: Trocamos 'vp.produto' pelo nome exato da variável: 'vp.produtoProcurado'
    @Query("SELECT vp.produtoProcurado, COUNT(vp.id) FROM VendaPerdida vp WHERE vp.dataRegistro >= :data GROUP BY vp.produtoProcurado ORDER BY COUNT(vp.id) DESC")
    List<Object[]> countVendasPerdidasAgrupadasDesde(@Param("data") LocalDateTime data);
}
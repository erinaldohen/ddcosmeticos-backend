package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.InsightIA;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InsightIARepository extends JpaRepository<InsightIA, Long> {

    // ✅ OTIMIZADO: Paginação inserida. O Dashboard de IA nunca vai travar o arranque do sistema.
    Page<InsightIA> findByResolvidoFalseOrderByCriticidadeAscDataGeracaoDesc(Pageable pageable);
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.InsightIA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InsightIARepository extends JpaRepository<InsightIA, Long> {

    // ✅ CORRIGIDO: Removido o 'Pageable' e regressado a 'List' para compatibilidade com o Service atual.
    List<InsightIA> findByResolvidoFalseOrderByCriticidadeAscDataGeracaoDesc();
}
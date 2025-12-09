// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/repository/VendaRepository.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório para operações de CRUD e busca na entidade Venda.
 */
@Repository
public interface VendaRepository extends JpaRepository<Venda, Long> {
    // Métodos de consulta futuros serão adicionados aqui (ex: findByDataBetween)
}
// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/repository/MovimentoEstoqueRepository.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório para operações de CRUD na entidade MovimentoEstoque.
 */
@Repository
public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {
    // Métodos de busca por produto ou tipo de movimento serão adicionados aqui.
}
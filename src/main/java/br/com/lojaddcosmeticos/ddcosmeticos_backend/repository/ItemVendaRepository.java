// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/repository/ItemVendaRepository.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda; // Importa a Entidade correta
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
// O Repositório deve ser de ItemVenda (não VendaItem)
public interface ItemVendaRepository extends JpaRepository<ItemVenda, Long> {

    // Você pode adicionar métodos customizados aqui se necessário
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByDocumento(String documento);
    Optional<Cliente> findByTelefone(String telefone);

    Page<Cliente> findByTipoPessoa(String tipoPessoa, Pageable pageable);
    Page<Cliente> findByNomeContainingIgnoreCase(String nome, Pageable pageable);

    // =========================================================================
    // CONSULTAS LEVES DE ALTA PERFORMANCE (USADAS NO AUTOCOMPLETE DO PDV)
    // =========================================================================

    interface ClienteResumoProjection {
        Long getId();
        String getNome();
        String getDocumento();
        String getTelefone();
    }

    @Query("SELECT c.id as id, c.nome as nome, c.documento as documento, c.telefone as telefone FROM Cliente c WHERE c.ativo = true AND (LOWER(c.nome) LIKE LOWER(CONCAT('%', :termo, '%')) OR c.documento LIKE CONCAT('%', :termo, '%') OR c.telefone LIKE CONCAT('%', :termo, '%'))")
    List<ClienteResumoProjection> buscarParaPDV(@Param("termo") String termo, Pageable pageable);
}
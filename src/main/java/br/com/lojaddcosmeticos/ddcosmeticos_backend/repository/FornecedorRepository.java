package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {

    // Busca inteligente para a tela de listagem
    @Query("SELECT f FROM Fornecedor f WHERE " +
            "(LOWER(f.nomeFantasia) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "LOWER(f.razaoSocial) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "f.cnpj LIKE CONCAT('%', :termo, '%')) AND f.ativo = true")
    Page<Fornecedor> buscarAtivosPorTermo(String termo, Pageable pageable);

    Optional<Fornecedor> findByCnpj(String cnpj);
    boolean existsByCnpj(String cnpj);
}
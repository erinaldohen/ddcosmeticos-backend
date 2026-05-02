package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {

    // Busca principal usada na listagem (Filtra por Ativo = true)
    @Query("SELECT f FROM Fornecedor f WHERE " +
            "(LOWER(f.nomeFantasia) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "LOWER(f.razaoSocial) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "f.cnpj LIKE CONCAT('%', :termo, '%')) AND f.ativo = true")
    Page<Fornecedor> buscarPorTermo(@Param("termo") String termo, Pageable pageable);

    // Método CRÍTICO para a correção do erro 500 no cadastro
    // Permite que o Controller busque se o CNPJ já existe
    Optional<Fornecedor> findByCnpj(String cnpj);

    // Método auxiliar leve para validações rápidas
    boolean existsByCnpj(String cnpj);
    Optional<Fornecedor> findByRazaoSocialIgnoreCase(String razaoSocial);

    // =========================================================================
    // CONSULTAS LEVES DE ALTA PERFORMANCE (USADAS NO SELECT DE PRODUTOS)
    // =========================================================================

    interface FornecedorResumoProjection {
        Long getId();
        String getNomeFantasia();
        String getCnpj();
    }

    @Query("SELECT f.id as id, f.nomeFantasia as nomeFantasia, f.cnpj as cnpj FROM Fornecedor f WHERE f.ativo = true ORDER BY f.nomeFantasia ASC")
    List<FornecedorResumoProjection> listarNomesFornecedores();
}
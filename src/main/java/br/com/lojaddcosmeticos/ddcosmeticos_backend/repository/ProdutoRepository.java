package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param; // Adicionado para segurança
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // ==================================================================================
    // SESSÃO 1: BUSCAS PADRÃO (JPA)
    // ==================================================================================

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    // Método auxiliar para checar duplicidade
    boolean existsByCodigoBarras(String codigoBarras);

    List<Produto> findAllByAtivoTrue();

    // Busca por parte do nome (Case Insensitive)
    List<Produto> findByDescricaoContainingIgnoreCase(String descricao);

    // Busca Inteligente: Procura por parte do nome OU código exato
    List<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras);

    // ==================================================================================
    // SESSÃO 2: QUERIES PERSONALIZADAS (JPQL & NATIVE)
    // ==================================================================================

    /**
     * Busca um produto pelo EAN mesmo que esteja inativo (Native Query).
     */
    @Query(value = "SELECT * FROM produto WHERE codigo_barras = :ean", nativeQuery = true)
    Optional<Produto> findByEanIrrestrito(@Param("ean") String ean);

    /**
     * Reativa um produto logicamente.
     */
    @Modifying
    @Query("UPDATE Produto p SET p.ativo = true WHERE p.id = :id")
    void reativarProduto(@Param("id") Long id);

    /**
     * Conta produtos com estoque abaixo ou igual ao mínimo.
     * COALESCE garante que se o estoqueMinimo for nulo, conta como 0.
     */
    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    Long contarProdutosAbaixoDoMinimo();
}
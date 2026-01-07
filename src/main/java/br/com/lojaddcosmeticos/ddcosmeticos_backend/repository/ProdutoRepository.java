package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // --- 1. MÉTODOS DE BUSCA BÁSICA ---
    boolean existsByCodigoBarras(String codigoBarras);

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    // Essencial para VendaService (Evita N+1 queries)
    List<Produto> findByCodigoBarrasIn(List<String> codigos);

    // Busca Paginada (Admin)
    Page<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras, Pageable pageable);

    // --- 2. CATÁLOGO VISUAL ---

    // Busca Inteligente (Nome, Marca, Categoria ou EAN)
    @Query("SELECT p FROM Produto p WHERE " +
            "(LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.marca) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(p.codigoBarras LIKE CONCAT('%', :termo, '%'))")
    List<Produto> buscarInteligente(@Param("termo") String termo);

    // Busca Otimizada (Top 50 ativos recentes)
    List<Produto> findTop50ByAtivoTrueOrderByIdDesc();

    // --- 3. DASHBOARD E RELATÓRIOS ---

    // ✅ MÉTODO RESTAURADO: Contagem para o Dashboard
    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    Long contarProdutosAbaixoDoMinimo();

    // Lista produtos com estoque baixo (para alertas)
    @Query("SELECT p FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    List<Produto> findProdutosComBaixoEstoque();

    // --- 4. MANUTENÇÃO E SISTEMA ---

    // Busca ignorando soft delete (para validação de cadastro duplicado)
    @Query(value = "SELECT * FROM produto WHERE codigo_barras = :ean", nativeQuery = true)
    Optional<Produto> findByEanIrrestrito(@Param("ean") String ean);

    // Lixeira (Apenas inativos)
    @Query(value = "SELECT * FROM produto WHERE ativo = false", nativeQuery = true)
    List<Produto> findAllLixeira();

    // Restaurar produto
    @Modifying
    @Query("UPDATE Produto p SET p.ativo = true WHERE p.id = :id")
    void reativarProduto(@Param("id") Long id);
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // --- 1. MÉTODOS DE BUSCA BÁSICA ---
    boolean existsByCodigoBarras(String codigoBarras);

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    List<Produto> findByCodigoBarrasIn(List<String> codigos);

    // [ADICIONADO] Necessário para o método realizarSaneamentoFiscal no Service
    List<Produto> findAllByAtivoTrue();

    // Busca Paginada (Para o Grid)
    Page<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras, Pageable pageable);

    // [ADICIONADO] Busca em Lista (Para o Select/Combobox ou métodos internos sem paginação)
    List<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras);

    // --- 2. CATÁLOGO VISUAL ---

    @Query("SELECT p FROM Produto p WHERE " +
            "(LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.marca) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(p.codigoBarras LIKE CONCAT('%', :termo, '%'))")
    List<Produto> buscarInteligente(@Param("termo") String termo);

    List<Produto> findTop50ByAtivoTrueOrderByIdDesc();

    // --- NOVO MÉTODO PARA CORRIGIR O TESTE ---
    List<Produto> findTop10ByOrderByPrecoVendaDesc();

    // --- 3. DASHBOARD E RELATÓRIOS ---

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    Long contarProdutosAbaixoDoMinimo();

    @Query("SELECT p FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    List<Produto> findProdutosComBaixoEstoque();

    long countByQuantidadeEmEstoqueLessThanEqualAndAtivoTrue(Integer qtd);

    @Query("SELECT COALESCE(SUM(p.precoCusto * p.quantidadeEmEstoque), 0) FROM Produto p WHERE p.ativo = true")
    BigDecimal calcularValorTotalEstoque();

    @Query("SELECT COUNT(p) FROM Produto p WHERE (p.ncm IS NULL OR p.cest IS NULL) AND p.ativo = true")
    long contarProdutosSemFiscal();

    // --- 4. MANUTENÇÃO ---

    @Query(value = "SELECT * FROM produto WHERE codigo_barras = :ean", nativeQuery = true)
    Optional<Produto> findByEanIrrestrito(@Param("ean") String ean);

    @Query(value = "SELECT * FROM produto WHERE ativo = false", nativeQuery = true)
    List<Produto> findAllLixeira();

    @Modifying
    @Query("UPDATE Produto p SET p.ativo = true WHERE p.id = :id")
    void reativarProduto(@Param("id") Long id);
}
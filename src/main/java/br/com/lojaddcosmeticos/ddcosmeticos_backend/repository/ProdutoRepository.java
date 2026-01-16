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

    List<Produto> findAllByAtivoTrue();

    // --- NOVO MÉTODO (Correção do Erro) ---
    // Usado pelo EstoqueService para filtrar produtos pelo NCM antes da comparação de nomes
    List<Produto> findByNcm(String ncm);
    // --------------------------------------

    // Busca Paginada (Para o Grid)
    Page<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras, Pageable pageable);

    // Busca em Lista (Para Selects)
    List<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras);

    // --- 2. CATÁLOGO VISUAL ---
    @Query("SELECT p FROM Produto p WHERE " +
            "(LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.marca) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(p.codigoBarras LIKE CONCAT('%', :termo, '%'))")
    List<Produto> buscarInteligente(@Param("termo") String termo);

    List<Produto> findTop50ByAtivoTrueOrderByIdDesc();
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

    // --- 4. MANUTENÇÃO / LIXEIRA ---

    // Busca inclusive INATIVOS (Usado para validar duplicidade e reativar)
    @Query(value = "SELECT * FROM produto WHERE codigo_barras = :ean", nativeQuery = true)
    Optional<Produto> findByEanIrrestrito(@Param("ean") String ean);

    // Lista da Lixeira (Usado pelo AuditoriaService)
    @Query(value = "SELECT * FROM produto WHERE ativo = false", nativeQuery = true)
    List<Produto> findAllLixeira();

    // Reativar por ID (Usado pelo AuditoriaService)
    @Modifying
    @Query("UPDATE Produto p SET p.ativo = true WHERE p.id = :id")
    void reativarProduto(@Param("id") Long id);

    // --- 5. INTELIGÊNCIA DE COMPRAS (BI) ---

    /**
     * Busca produtos que estão com estoque baixo E que já foram fornecidos
     * pelo fornecedor especificado no histórico de movimentação.
     */
    @Query("""
        SELECT DISTINCT p 
        FROM Produto p 
        JOIN MovimentoEstoque m ON m.produto = p 
        WHERE m.fornecedor.id = :fornecedorId 
        AND (p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0))
        AND p.ativo = true
    """)
    List<Produto> findSugestaoCompraPorFornecedor(@Param("fornecedorId") Long fornecedorId);
}
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

    // --- MÉTODOS BÁSICOS ---
    boolean existsByCodigoBarras(String codigoBarras);
    List<Produto> findByCodigoBarrasIn(List<String> codigos);
    List<Produto> findByNcm(String ncm);

    // --- QUERY MESTRA DE FILTRAGEM (ALTA PERFORMANCE) ---
    // CORREÇÃO APLICADA: CAST(:termo AS text) impede que o Postgres interprete o parâmetro como bytea.
    @Query("SELECT p FROM Produto p WHERE p.ativo = true " +
            "AND (:termo IS NULL OR LOWER(p.descricao) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) OR p.codigoBarras LIKE CONCAT('%', CAST(:termo AS text), '%')) " +
            "AND (:marca IS NULL OR p.marca = :marca) " +
            "AND (:categoria IS NULL OR p.categoria = :categoria) " +
            "AND (:statusEstoque IS NULL " +
            "     OR (:statusEstoque = 'baixo' AND p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 5)) " +
            "     OR (:statusEstoque = 'ok' AND p.quantidadeEmEstoque > COALESCE(p.estoqueMinimo, 5))) " +
            "AND (:semImagem = false OR (:semImagem = true AND (p.urlImagem IS NULL OR p.urlImagem = ''))) " +
            "AND (:semNcm = false OR (:semNcm = true AND (p.ncm IS NULL OR p.ncm = '00000000' OR p.ncm = ''))) " +
            "AND (:precoZero = false OR (:precoZero = true AND p.precoVenda = 0))")
    Page<Produto> buscarComFiltros(
            @Param("termo") String termo,
            @Param("marca") String marca,
            @Param("categoria") String categoria,
            @Param("statusEstoque") String statusEstoque,
            @Param("semImagem") Boolean semImagem,
            @Param("semNcm") Boolean semNcm,
            @Param("precoZero") Boolean precoZero,
            Pageable pageable);

    // --- MÉTODOS LEGADOS ---

    // CORREÇÃO APLICADA: CAST(:termo AS text)
    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND (LOWER(p.descricao) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%')) OR p.codigoBarras LIKE CONCAT('%', CAST(:termo AS text), '%'))")
    Page<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(@Param("termo") String termo, Pageable pageable);

    // Sobrecarga (Spring Data resolve esta automaticamente pela assinatura do método)
    List<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras);

    // --- OUTROS MÉTODOS DE NEGÓCIO ---

    // CORREÇÃO APLICADA: CAST(:termo AS text)
    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND (" +
            "(LOWER(p.descricao) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%'))) OR " +
            "(LOWER(p.marca) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%'))) OR " +
            "(LOWER(p.categoria) LIKE LOWER(CONCAT('%', CAST(:termo AS text), '%'))) OR " +
            "(p.codigoBarras LIKE CONCAT('%', CAST(:termo AS text), '%')))")
    List<Produto> buscarInteligente(@Param("termo") String termo);

    List<Produto> findTop50ByAtivoTrueOrderByIdDesc();
    List<Produto> findTop10ByOrderByPrecoVendaDesc();

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    Long contarProdutosAbaixoDoMinimo();

    @Query("SELECT p FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    List<Produto> findProdutosComBaixoEstoque();

    long countByQuantidadeEmEstoqueLessThanEqualAndAtivoTrue(Integer qtd);

    @Query("SELECT COALESCE(SUM(p.precoCusto * p.quantidadeEmEstoque), 0) FROM Produto p WHERE p.ativo = true")
    BigDecimal calcularValorTotalEstoque();

    @Query("SELECT COUNT(p) FROM Produto p WHERE (p.ncm IS NULL OR p.cest IS NULL) AND p.ativo = true")
    long contarProdutosSemFiscal();

    @Query("""
        SELECT DISTINCT p 
        FROM Produto p 
        JOIN MovimentoEstoque m ON m.produto = p 
        WHERE m.fornecedor.id = :fornecedorId 
        AND (p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0))
        AND p.ativo = true
    """)
    List<Produto> findSugestaoCompraPorFornecedor(@Param("fornecedorId") Long fornecedorId);

    // --- QUERIES NATIVAS AJUSTADAS PARA POSTGRESQL ---

    @Query(value = "SELECT ncm FROM produto WHERE descricao ILIKE CONCAT('%', :palavraChave, '%') AND ncm <> '00000000' GROUP BY ncm ORDER BY COUNT(id) DESC LIMIT 1", nativeQuery = true)
    String findNcmMaisUsadoPorPalavra(@Param("palavraChave") String palavraChave);

    @Query(value = """
        SELECT ncm 
        FROM produto 
        WHERE descricao ILIKE CONCAT('%', :palavra, '%') 
          AND ncm IS NOT NULL 
          AND ncm != '' 
          AND ncm != '00000000'
        GROUP BY ncm 
        ORDER BY COUNT(*) DESC 
        LIMIT 1
    """, nativeQuery = true)
    String findNcmInteligente(@Param("palavra") String palavra);

    Page<Produto> findByFornecedorId(Long fornecedorId, Pageable pageable);

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    @Query("SELECT p FROM Produto p WHERE p.codigoBarras = :ean")
    Optional<Produto> findByEanIrrestrito(@Param("ean") String ean);

    @Query(value = "SELECT MAX(CAST(codigo_barras AS BIGINT)) FROM produto WHERE codigo_barras ~ '^[0-9]+$'", nativeQuery = true)
    Long findMaxCodigoBarras();

    @Query("SELECT MAX(p.id) FROM Produto p")
    Long findMaxId();

    List<Produto> findAllByAtivoTrue();

    @Query("SELECT p FROM Produto p WHERE p.ativo = false")
    List<Produto> findAllLixeira();

    @Modifying
    @Query("UPDATE Produto p SET p.ativo = true WHERE p.id = :id")
    void reativarProduto(Long id);

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.validade < CURRENT_DATE AND p.ativo = true")
    long countVencidos();

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque <= p.estoqueMinimo AND p.ativo = true")
    long countBaixoEstoque();
}
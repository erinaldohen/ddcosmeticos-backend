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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // --- MÉTODOS BÁSICOS ---
    boolean existsByCodigoBarras(String codigoBarras);
    List<Produto> findByCodigoBarrasIn(List<String> codigos);
    List<Produto> findByNcm(String ncm);
    // --- CROSS-SELL (SUGESTÕES) ---
    Page<Produto> findBySubcategoriaAndIdNotAndAtivoTrue(String subcategoria, Long id, Pageable pageable);

    // --- QUERY MESTRA DE FILTRAGEM (ALTA PERFORMANCE & CORRIGIDA PARA HIBERNATE 6) ---
    // Utiliza COALESCE para tratar parâmetros booleanos nulos, garantindo compatibilidade PostgreSQL
    @Query("SELECT p FROM Produto p WHERE p.ativo = true " +
            "AND (:termo IS NULL OR :termo = '' OR LOWER(p.descricao) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%')) OR p.codigoBarras LIKE CONCAT('%', CAST(:termo AS string), '%')) " +
            "AND (:marca IS NULL OR :marca = '' OR p.marca = :marca) " +
            "AND (:categoria IS NULL OR :categoria = '' OR p.categoria = :categoria) " +
            "AND (:statusEstoque IS NULL OR :statusEstoque = '' " +
            "     OR (:statusEstoque = 'baixo' AND p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 5)) " +
            "     OR (:statusEstoque = 'ok' AND p.quantidadeEmEstoque > COALESCE(p.estoqueMinimo, 5))) " +
            "AND (COALESCE(:semImagem, false) = false OR (p.urlImagem IS NULL OR p.urlImagem = '')) " +
            "AND (COALESCE(:semNcm, false) = false OR (p.ncm IS NULL OR p.ncm = '00000000' OR p.ncm = '')) " +
            "AND (COALESCE(:precoZero, false) = false OR (p.precoVenda IS NULL OR p.precoVenda <= 0)) " +
            "AND (COALESCE(:revisaoPendente, false) = false OR p.revisaoPendente = true)")
    Page<Produto> buscarComFiltros(
            @Param("termo") String termo,
            @Param("marca") String marca,
            @Param("categoria") String categoria,
            @Param("statusEstoque") String statusEstoque,
            @Param("semImagem") Boolean semImagem,
            @Param("semNcm") Boolean semNcm,
            @Param("precoZero") Boolean precoZero,
            @Param("revisaoPendente") Boolean revisaoPendente,
            Pageable pageable);

    // --- MÉTODOS LEGADOS ---
    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND (LOWER(p.descricao) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%')) OR p.codigoBarras LIKE CONCAT('%', CAST(:termo AS string), '%'))")
    Page<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(@Param("termo") String termo, Pageable pageable);

    List<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras);

    // --- OUTROS MÉTODOS DE NEGÓCIO ---
    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND (" +
            "(LOWER(p.descricao) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%'))) OR " +
            "(LOWER(p.marca) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%'))) OR " +
            "(LOWER(p.categoria) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%'))) OR " +
            "(p.codigoBarras LIKE CONCAT('%', CAST(:termo AS string), '%')))")
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
    void reativarProduto(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.validade < CURRENT_DATE AND p.ativo = true")
    long countVencidos();

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque <= p.estoqueMinimo AND p.ativo = true")
    long countBaixoEstoque();

    @Query("SELECT COALESCE(SUM(p.quantidadeEmEstoque), 0) FROM Produto p WHERE p.ativo = true")
    Long calcularQuantidadeTotalEstoque();

    // --- PROJEÇÕES PARA GESTÃO DE RISCO DE ESTOQUE (NOVO DASHBOARD) ---
    interface RiscoEstoqueProjection {
        Integer getItens();
        BigDecimal getValorRisco();
    }

    // 1. Produtos vencendo em X dias ou já vencidos (com estoque > 0)
    @Query("SELECT CAST(COUNT(p.id) AS int) as itens, " +
            "CAST(COALESCE(SUM(p.precoCusto * p.quantidadeEmEstoque), 0) AS bigdecimal) as valorRisco " +
            "FROM Produto p WHERE p.validade <= :dataLimite AND p.quantidadeEmEstoque > 0 AND p.ativo = true")
    RiscoEstoqueProjection calcularRiscoVencimento(@Param("dataLimite") LocalDate dataLimite);

    // 2. Estoque Parado / Curva C (Com estoque > 0, mas sem giro nos últimos X dias)
    @Query("SELECT CAST(COUNT(p.id) AS int) as itens, " +
            "CAST(COALESCE(SUM(p.precoCusto * p.quantidadeEmEstoque), 0) AS bigdecimal) as valorRisco " +
            "FROM Produto p WHERE p.quantidadeEmEstoque > 0 AND p.ativo = true AND " +
            "(p.dataUltimaVenda IS NULL OR p.dataUltimaVenda <= :dataLimiteGiro)")
    RiscoEstoqueProjection calcularEstoqueParado(@Param("dataLimiteGiro") LocalDate dataLimiteGiro);

    // --- CONTADORES DE DASHBOARD ---

    // Simplificamos dois métodos idênticos num só, que calcula todos os produtos ativos do sistema
    @Query("SELECT COUNT(p) FROM Produto p WHERE p.ativo = true")
    long contarProdutosAtivos();

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.revisaoPendente = true AND p.ativo = true")
    long countProdutosPendentesDeRevisao();

    // --- CROSS-SELL INTELIGENTE (COMPLEMENTARES) ---

    // 1. Busca por subcategorias complementares exatas mapeadas pela IA
    @Query("SELECT p FROM Produto p WHERE UPPER(p.subcategoria) IN :subcategorias AND p.id != :id AND p.ativo = true AND p.quantidadeEmEstoque > 0 ORDER BY p.quantidadeEmEstoque DESC")
    Page<Produto> findComplementares(@Param("subcategorias") List<String> subcategorias, @Param("id") Long id, Pageable pageable);

    // 2. FALLBACK: Mesma Categoria GERAL, mas Subcategoria DIFERENTE (Evita oferecer o mesmo tipo de produto)
    @Query("SELECT p FROM Produto p WHERE p.categoria = :categoria AND p.subcategoria != :subcategoria AND p.id != :id AND p.ativo = true AND p.quantidadeEmEstoque > 0 ORDER BY p.quantidadeEmEstoque DESC")
    Page<Produto> findByCategoriaAndSubcategoriaNotAndIdNotAndAtivoTrue(
            @Param("categoria") String categoria,
            @Param("subcategoria") String subcategoria,
            @Param("id") Long id,
            Pageable pageable);
    // =========================================================================================
    // 🔥 BUSCA CIRÚRGICA PARA O ROBÔ DE SANEAMENTO GS1 🔥
    // =========================================================================================
    @Query("SELECT p FROM Produto p WHERE p.codigoBarras LIKE '2%' AND length(p.codigoBarras) = 13")
    List<Produto> findProdutosComEanInterno();
    boolean existsByHashImagem(String hashImagem);
    // Pega os produtos que não têm foto E que ainda não falharam no motor MVC
    List<Produto> findTop5ByUrlImagemIsNullAndRevisaoImagemPendenteFalseOrderByIdAsc();
}
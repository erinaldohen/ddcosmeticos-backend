package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // =======================================================
    // 🔥 INTEGRAÇÃO COM FORNECEDORES
    // =======================================================
    Page<Produto> findByFornecedorId(Long fornecedorId, Pageable pageable);

    // =======================================================
    // 🔥 VISÃO COMPUTACIONAL E UPLOADS
    // =======================================================
    boolean existsByHashImagem(String hashImagem);

    // =======================================================
    // 🔥 QUERIES DO CATÁLOGO (FRONTEND WEB)
    // =======================================================
    List<Produto> findTop50ByAtivoTrueOrderByIdDesc();

    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND (" +
            "LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "LOWER(p.marca) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "p.codigoBarras LIKE CONCAT('%', :termo, '%'))")
    List<Produto> buscarInteligente(@Param("termo") String termo);

    // =======================================================
    // 🔥 PESQUISA AVANÇADA (BACKOFFICE) - CORRIGIDA
    // =======================================================
    @Query("SELECT p FROM Produto p WHERE " +
            "(:termo IS NULL OR LOWER(p.descricao) LIKE :termo OR LOWER(p.codigoBarras) LIKE :termo) AND " +
            "(:marca IS NULL OR LOWER(p.marca) = :marca) AND " +
            "(:categoria IS NULL OR LOWER(p.categoria) = :categoria) AND " +
            "(:semImagem = false OR p.urlImagem IS NULL OR p.urlImagem = '') AND " +
            "(:semNcm = false OR p.ncm IS NULL OR p.ncm = '' OR p.ncm = '00000000') AND " +
            "(:precoZero = false OR p.precoVenda IS NULL OR p.precoVenda <= 0) AND " +
            "(:revisaoPendente = false OR p.revisaoPendente = true) AND " +
            "p.ativo = true " +
            "ORDER BY " +
            "CASE " +
            "  WHEN :statusEstoque = 'ZERADO' AND p.quantidadeEmEstoque = 0 THEN 0 " +
            "  WHEN :statusEstoque = 'BAIXO' AND p.quantidadeEmEstoque > 0 AND p.quantidadeEmEstoque <= p.estoqueMinimo THEN 0 " +
            "  ELSE 1 END ASC, p.id DESC")
    Page<Produto> buscarComFiltros(
            @Param("termo") String termo,
            @Param("marca") String marca,
            @Param("categoria") String categoria,
            @Param("statusEstoque") String statusEstoque,
            @Param("semImagem") Boolean semImagem,
            @Param("semNcm") Boolean semNcm,
            @Param("precoZero") Boolean precoZero,
            @Param("revisaoPendente") Boolean revisaoPendente,
            Pageable pageable
    );

    // =======================================================
    // 🔥 MÉTODOS DE INTELIGÊNCIA ARTIFICIAL E LIXEIRA
    // =======================================================
    @Query("SELECT p FROM Produto p WHERE p.ativo = false")
    List<Produto> findAllLixeira();

    Optional<Produto> findByCodigoBarrasAndAtivoTrue(String codigoBarras);

    @Query("SELECT p FROM Produto p WHERE p.codigoBarras = :codigoBarras")
    Optional<Produto> findByEanIrrestrito(@Param("codigoBarras") String codigoBarras);

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    @Query("SELECT MAX(p.id) FROM Produto p")
    Long findMaxId();

    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND p.quantidadeEmEstoque <= p.estoqueMinimo AND p.quantidadeEmEstoque > 0")
    List<Produto> findProdutosComBaixoEstoque();

    List<Produto> findAllByAtivoTrue();

    @Query("SELECT p.ncm FROM Produto p WHERE LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%')) AND p.ncm IS NOT NULL AND p.ncm <> '00000000' GROUP BY p.ncm ORDER BY COUNT(p.id) DESC LIMIT 1")
    String findNcmInteligente(@Param("termo") String termo);

    @Query("SELECT p FROM Produto p WHERE p.codigoBarras LIKE '200%' AND LENGTH(p.codigoBarras) = 13")
    List<Produto> findProdutosComEanInterno();

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.ativo = true AND p.revisaoPendente = true")
    long countProdutosPendentesDeRevisao();

    @Query("SELECT new map(" +
            "SUM(CASE WHEN (p.precoCusto IS NULL OR p.precoCusto <= 0) THEN 1 ELSE 0 END) as semCusto, " +
            "SUM(CASE WHEN (p.precoVenda IS NULL OR p.precoVenda <= 0) THEN 1 ELSE 0 END) as precoVendaZerado, " +
            "SUM(CASE WHEN (p.ncm IS NULL OR p.ncm = '') THEN 1 ELSE 0 END) as semNcm, " +
            "SUM(CASE WHEN (LENGTH(p.ncm) > 0 AND LENGTH(p.ncm) < 8 AND p.ncm <> '00000000') THEN 1 ELSE 0 END) as ncmInvalido, " +
            "SUM(CASE WHEN (p.descricao IS NULL OR p.descricao = '' OR p.descricao LIKE '%S/ NOME%') THEN 1 ELSE 0 END) as semDescricao, " +
            "SUM(CASE WHEN (p.marca IS NULL OR p.marca = '') THEN 1 ELSE 0 END) as semMarca, " +
            "SUM(CASE WHEN (p.alertaGondola = true) THEN 1 ELSE 0 END) as divergenciaGondola" +
            ") FROM Produto p WHERE p.ativo = true")
    Map<String, Long> countAnomaliasIA();

    @Query("SELECT p FROM Produto p WHERE (LOWER(p.descricao) IN :complementos) AND p.id <> :produtoBaseId AND p.ativo = true")
    Page<Produto> findComplementares(@Param("complementos") List<String> complementos, @Param("produtoBaseId") Long produtoBaseId, Pageable pageable);

    Page<Produto> findByCategoriaAndSubcategoriaNotAndIdNotAndAtivoTrue(String categoria, String subcategoria, Long id, Pageable pageable);
}
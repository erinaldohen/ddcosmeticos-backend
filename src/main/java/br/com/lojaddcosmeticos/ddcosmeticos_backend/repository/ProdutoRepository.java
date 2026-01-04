package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
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

    // ==================================================================================
    // SESSÃO 1: BUSCAS PADRÃO E OTIMIZADAS
    // ==================================================================================

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    // ESSENCIAL PARA VENDA SERVICE (Correção N+1)
    List<Produto> findByCodigoBarrasIn(List<String> codigos);

    boolean existsByCodigoBarras(String codigoBarras);

    List<Produto> findAllByAtivoTrue();

    List<Produto> findByDescricaoContainingIgnoreCase(String descricao);

    List<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras);

    // Suporte a paginação nas buscas
    Page<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarrasContainingIgnoreCase(String descricao, String codigoBarras, Pageable pageable);

    // ==================================================================================
    // SESSÃO 2: PROJEÇÕES LEVES (DTOs)
    // ==================================================================================

    @Query("""
       SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO(
           p.id, p.descricao, p.precoVenda, p.urlImagem, 
           p.quantidadeEmEstoque, p.ativo, p.codigoBarras, p.marca, p.ncm
       ) 
       FROM Produto p
    """)
    List<ProdutoListagemDTO> findAllResumo();

    @Query("""
       SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO(
           p.id, p.descricao, p.precoVenda, p.urlImagem, 
           p.quantidadeEmEstoque, p.ativo, p.codigoBarras, p.marca, p.ncm
       ) 
       FROM Produto p 
       WHERE p.codigoBarras = :termo OR LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%'))
    """)
    List<ProdutoListagemDTO> buscarPorTermo(@Param("termo") String termo);

    // ==================================================================================
    // SESSÃO 3: BUSCAS INTELIGENTES E INDICADORES
    // ==================================================================================

    @Query("SELECT p FROM Produto p WHERE " +
            "(LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.marca) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(p.codigoBarras LIKE CONCAT('%', :termo, '%'))")
    List<Produto> buscarInteligente(@Param("termo") String termo);

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    Long contarProdutosAbaixoDoMinimo();

    // ==================================================================================
    // SESSÃO 4: MANUTENÇÃO E AUDITORIA (Native Queries)
    // ==================================================================================

    @Query(value = "SELECT * FROM produto WHERE codigo_barras = :ean", nativeQuery = true)
    Optional<Produto> findByEanIrrestrito(@Param("ean") String ean);

    @Modifying
    @Query("UPDATE Produto p SET p.ativo = true WHERE p.id = :id")
    void reativarProduto(@Param("id") Long id);

    // CORREÇÃO DO ERRO LINHA 73 (AuditoriaService)
    @Query(value = "SELECT * FROM produto WHERE ativo = false", nativeQuery = true)
    List<Produto> findAllLixeira();
}
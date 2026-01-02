package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
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
    // SESSÃO 1: BUSCAS PADRÃO (JPA)
    // ==================================================================================

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarras(String codigoBarras);

    List<Produto> findAllByAtivoTrue();

    List<Produto> findByDescricaoContainingIgnoreCase(String descricao);

    List<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras);

    // ==================================================================================
    // SESSÃO 2: QUERIES PERSONALIZADAS (JPQL & NATIVE)
    // ==================================================================================

    @Query(value = "SELECT * FROM produto WHERE codigo_barras = :ean", nativeQuery = true)
    Optional<Produto> findByEanIrrestrito(@Param("ean") String ean);

    @Modifying
    @Query("UPDATE Produto p SET p.ativo = true WHERE p.id = :id")
    void reativarProduto(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    Long contarProdutosAbaixoDoMinimo();

    @Query("""
       SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO(
           p.id, 
           p.descricao, 
           p.precoVenda, 
           p.urlImagem, 
           p.quantidadeEmEstoque, 
           p.ativo,
           p.codigoBarras,  
           p.marca,         
           p.ncm            
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
}
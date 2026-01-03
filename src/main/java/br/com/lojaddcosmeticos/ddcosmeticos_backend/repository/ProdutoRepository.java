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
    // SESSÃO 1: BUSCAS PADRÃO E EXATAS (JPA Padrão)
    // Foco: Recuperação simples por chave ou filtro básico.
    // ==================================================================================

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarras(String codigoBarras);

    List<Produto> findAllByAtivoTrue();

    List<Produto> findByDescricaoContainingIgnoreCase(String descricao);

    // Buscas combinadas (Nome OU Código)
    List<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarras(String descricao, String codigoBarras);
    Page<Produto> findByDescricaoContainingIgnoreCaseOrCodigoBarrasContainingIgnoreCase(String descricao, String codigoBarras, Pageable pageable);


    // ==================================================================================
    // SESSÃO 2: PROJEÇÕES E DTOS (Performance)
    // Foco: Listagens leves para o frontend (traz apenas os dados necessários).
    // ==================================================================================

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


    // ==================================================================================
    // SESSÃO 3: INTELIGÊNCIA E CATÁLOGO (Módulo Smart Search)
    // Foco: Buscas complexas que varrem múltiplos campos (Google Style).
    // ==================================================================================

    @Query("SELECT p FROM Produto p WHERE " +
            "(LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.marca) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(LOWER(p.subcategoria) LIKE LOWER(CONCAT('%', :termo, '%'))) OR " +
            "(p.codigoBarras LIKE CONCAT('%', :termo, '%'))")
    List<Produto> buscarInteligente(@Param("termo") String termo);


    // ==================================================================================
    // SESSÃO 4: INDICADORES E ESTOQUE (Módulo Dashboard/IA)
    // Foco: Contagens e regras de negócio para relatórios.
    // ==================================================================================

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque <= COALESCE(p.estoqueMinimo, 0) AND p.ativo = true")
    Long contarProdutosAbaixoDoMinimo();


    // ==================================================================================
    // SESSÃO 5: ADMINISTRATIVO E MANUTENÇÃO (Native Queries)
    // Foco: Acesso direto ao banco para correções ou atualizações em massa.
    // ==================================================================================

    // Busca ignorando o Soft Delete (traz até os inativos/excluídos)
    @Query(value = "SELECT * FROM produto WHERE codigo_barras = :ean", nativeQuery = true)
    Optional<Produto> findByEanIrrestrito(@Param("ean") String ean);

    @Modifying
    @Query("UPDATE Produto p SET p.ativo = true WHERE p.id = :id")
    void reativarProduto(@Param("id") Long id);

    // Lista apenas o que está na "Lixeira" (Inativo/Deletado)
    @Query(value = "SELECT * FROM produto WHERE ativo = false", nativeQuery = true)
    List<Produto> findAllLixeira();
}
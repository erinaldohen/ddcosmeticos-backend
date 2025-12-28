package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    // CORREÇÃO: Mudado de 'Nome' para 'Descricao'
    List<Produto> findByDescricaoContainingIgnoreCase(String descricao);

    List<Produto> findAllByAtivoTrue();

    @Query(value = "SELECT * FROM produto WHERE codigo_barras = :ean", nativeQuery = true)
    Optional<Produto> findByEanIrrestrito(String ean);

    @Modifying
    @Query("UPDATE Produto p SET p.ativo = true WHERE p.id = :id")
    void reativarProduto(Long id);

    @Query("SELECT COUNT(p) FROM Produto p WHERE p.quantidadeEmEstoque < p.estoqueMinimo AND p.ativo = true")
    Long contarProdutosAbaixoDoMinimo();
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.LoteProduto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoteProdutoRepository extends JpaRepository<LoteProduto, Long> {

    // Busca cirúrgica exata de lote (O(1))
    Optional<LoteProduto> findByProdutoAndNumeroLote(Produto produto, String numeroLote);

    // ✅ MANTIDO EM LISTA: Uso exclusivo do Motor FEFO (First-Expired-First-Out)
    // Retorna a quantidade restrita de lotes de 1 único produto para abater stock na venda.
    @Query("SELECT l FROM LoteProduto l WHERE l.produto = :produto AND l.quantidadeAtual > 0 ORDER BY l.dataValidade ASC")
    List<LoteProduto> findLotesDisponiveis(@Param("produto") Produto produto);
}
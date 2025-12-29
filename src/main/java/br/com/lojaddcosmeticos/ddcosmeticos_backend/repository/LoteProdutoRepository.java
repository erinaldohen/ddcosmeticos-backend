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

    // Busca lote específico para adicionar saldo
    Optional<LoteProduto> findByProdutoAndNumeroLote(Produto produto, String numeroLote);

    // Busca lotes com saldo, ordenados por validade (O que vence primeiro sai primeiro - FEFO)
    @Query("SELECT l FROM LoteProduto l WHERE l.produto = :produto AND l.quantidadeAtual > 0 ORDER BY l.dataValidade ASC")
    List<LoteProduto> findLotesDisponiveis(@Param("produto") Produto produto);
}
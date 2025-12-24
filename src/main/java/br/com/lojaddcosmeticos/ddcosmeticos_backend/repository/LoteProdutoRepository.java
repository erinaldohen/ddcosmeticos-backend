package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.LoteProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoteProdutoRepository extends JpaRepository<LoteProduto, Long> {

    // Método útil para buscar todos os lotes de um produto específico
    // Usaremos isso futuramente para exibir a validade no front
    List<LoteProduto> findByProdutoId(Long produtoId);
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {

    // Busca todo o histórico de um produto específico (Kardex)
    // Ordenado do mais recente para o mais antigo
    List<MovimentoEstoque> findByProdutoIdOrderByDataMovimentoDesc(Long idProduto);
}
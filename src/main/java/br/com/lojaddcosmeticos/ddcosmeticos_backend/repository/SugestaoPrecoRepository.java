package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.SugestaoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SugestaoPrecoRepository extends JpaRepository<SugestaoPreco, Long> {

    // Busca todas as pendentes para o painel do gerente
    List<SugestaoPreco> findByStatus(SugestaoPreco.StatusSugestao status);

    // Evita criar sugestão duplicada para o mesmo produto
    boolean existsByProdutoAndStatus(Produto produto, SugestaoPreco.StatusSugestao status);
}
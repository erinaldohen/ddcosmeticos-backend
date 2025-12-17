package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusSugestao; // <--- ADICIONE ESTE IMPORT
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.SugestaoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SugestaoPrecoRepository extends JpaRepository<SugestaoPreco, Long> {

    // CORREÇÃO: Usar 'StatusSugestao' direto, sem 'SugestaoPreco.' antes
    List<SugestaoPreco> findByStatus(StatusSugestao statusSugestao);

    // CORREÇÃO: Usar 'StatusSugestao' direto aqui também
    boolean existsByProdutoAndStatus(Produto produto, StatusSugestao status);
}
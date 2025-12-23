package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusSugestao; // <--- CONFIRA ESTE IMPORT
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.SugestaoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SugestaoPrecoRepository extends JpaRepository<SugestaoPreco, Long> {

    // O método DEVE usar StatusSugestao
    List<SugestaoPreco> findByStatus(StatusSugestao status);

    boolean existsByProdutoAndStatus(Produto produto, StatusSugestao status);
}
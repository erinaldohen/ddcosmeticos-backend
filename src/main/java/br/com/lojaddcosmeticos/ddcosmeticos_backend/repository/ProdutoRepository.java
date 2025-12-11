package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; // Import Obrigatório

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // CORREÇÃO: O retorno DEVE ser Optional<Produto> para que o .orElseThrow() funcione no Service
    Optional<Produto> findByCodigoBarras(String codigoBarras);
}
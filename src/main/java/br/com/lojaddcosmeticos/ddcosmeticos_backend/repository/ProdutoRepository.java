package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    Optional<Produto> findByCodigoBarras(String codigoBarras);

    // Busca TODOS (Para o Inventário Gerencial)
    List<Produto> findAllByAtivoTrue();

    // Busca SÓ OS FISCAIS (Para o Inventário Contábil)
    List<Produto> findByAtivoTrueAndPossuiNfEntradaTrue();
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ProdutoFornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProdutoFornecedorRepository extends JpaRepository<ProdutoFornecedor, Long> {
    Optional<ProdutoFornecedor> findByFornecedorAndCodigoNoFornecedor(Fornecedor fornecedor, String codigoNoFornecedor);
    boolean existsByFornecedorAndCodigoNoFornecedor(Fornecedor fornecedor, String codigoNoFornecedor);
}
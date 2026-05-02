package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ProdutoFornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProdutoFornecedorRepository extends JpaRepository<ProdutoFornecedor, Long> {

    // ✅ VALIDADO: Busca restrita a um único cruzamento, à prova de falhas.
    Optional<ProdutoFornecedor> findByFornecedorAndCodigoNoFornecedor(Fornecedor fornecedor, String codigoNoFornecedor);

    // ✅ VALIDADO: Excelente uso do existsBy para validações leves antes de salvar (O(1)).
    boolean existsByFornecedorAndCodigoNoFornecedor(Fornecedor fornecedor, String codigoNoFornecedor);
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {

    // Método padrão para buscar por produto
    List<MovimentoEstoque> findByProdutoId(Long produtoId);

    // --- O MÉTODO QUE ESTAVA FALTANDO ---
    // Verifica se já existe uma ENTRADA com este Número de Doc e Fornecedor
    boolean existsByDocumentoReferenciaAndFornecedorAndTipoMovimentoEstoque(
            String documentoReferencia,
            Fornecedor fornecedor,
            TipoMovimentoEstoque tipoMovimentoEstoque
    );
}
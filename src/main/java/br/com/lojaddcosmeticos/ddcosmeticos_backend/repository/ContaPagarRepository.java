package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long> {

    // Busca contas pendentes (útil para o dashboard financeiro)
    List<ContaPagar> findByStatus(ContaPagar.StatusConta status);

    // Busca histórico de contas de um fornecedor
    List<ContaPagar> findByFornecedorId(Long idFornecedor);
}
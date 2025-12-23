package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPrecificacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.SugestaoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SugestaoPrecoRepository extends JpaRepository<SugestaoPreco, Long> {

    // O nome do método AGORA corresponde ao campo 'statusPrecificacao' da entidade
    List<SugestaoPreco> findByStatusPrecificacao(StatusPrecificacao statusPrecificacao);

    boolean existsByProdutoAndStatusPrecificacao(Produto produto, StatusPrecificacao statusPrecificacao);
}
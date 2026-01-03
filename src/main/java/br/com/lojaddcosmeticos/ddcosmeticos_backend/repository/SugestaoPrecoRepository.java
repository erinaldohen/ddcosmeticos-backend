package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPrecificacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.SugestaoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SugestaoPrecoRepository extends JpaRepository<SugestaoPreco, Long> {

    // Nome ajustado para bater com o campo da Entidade
    List<SugestaoPreco> findByStatusPrecificacao(StatusPrecificacao statusPrecificacao);

    // Necessário para o Service encontrar uma sugestão específica de um produto
    Optional<SugestaoPreco> findByProdutoAndStatusPrecificacao(Produto produto, StatusPrecificacao status);

    boolean existsByProdutoAndStatusPrecificacao(Produto produto, StatusPrecificacao statusPrecificacao);
}
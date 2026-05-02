package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPrecificacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.SugestaoPreco;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SugestaoPrecoRepository extends JpaRepository<SugestaoPreco, Long> {

    // ✅ OTIMIZADO: Paginação adicionada para não travar o ecrã de "Aprovações Pendentes"
    Page<SugestaoPreco> findByStatusPrecificacao(StatusPrecificacao statusPrecificacao, Pageable pageable);

    // MÉTODOS CIRÚRGICOS MANTIDOS
    Optional<SugestaoPreco> findByProdutoAndStatusPrecificacao(Produto produto, StatusPrecificacao status);

    boolean existsByProdutoAndStatusPrecificacao(Produto produto, StatusPrecificacao statusPrecificacao);
}
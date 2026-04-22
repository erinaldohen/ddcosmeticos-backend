package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.NotaPendenteImportacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface NotaPendenteImportacaoRepository extends JpaRepository<NotaPendenteImportacao, Long> {
    Optional<NotaPendenteImportacao> findByChaveAcesso(String chaveAcesso);
    boolean existsByChaveAcesso(String chaveAcesso);
}
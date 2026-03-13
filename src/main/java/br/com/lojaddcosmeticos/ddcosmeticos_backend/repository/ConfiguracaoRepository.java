package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracaoRepository extends JpaRepository<ConfiguracaoLoja, Long> {

    // Como o sistema só tem 1 linha de configuração global, este método
    // garante que pegamos sempre essa primeira linha correta.
    ConfiguracaoLoja findFirstByOrderByIdAsc();

}
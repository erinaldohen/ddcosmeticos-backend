package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracaoLojaRepository extends JpaRepository<ConfiguracaoLoja, Long> {
    // Não precisa de métodos extras, pois só teremos 1 registro de configuração no banco.
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracaoLojaRepository extends JpaRepository<ConfiguracaoLoja, Long> {

    /**
     * Busca a primeira configuração encontrada no banco (ordem por ID).
     * Como o sistema só deve ter uma configuração de loja, isso retorna a configuração ativa.
     */
    Optional<ConfiguracaoLoja> findFirstByOrderByIdAsc();

}
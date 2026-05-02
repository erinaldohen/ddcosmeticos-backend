package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracaoLojaRepository extends JpaRepository<ConfiguracaoLoja, Long> {

    // Método seguro que retorna a configuração completa
    Optional<ConfiguracaoLoja> findFirstByOrderByIdAsc();

    // Verificação ultra-rápida de instalação do sistema
    @Query("SELECT COUNT(c) > 0 FROM ConfiguracaoLoja c")
    boolean isLojaConfigurada();
}
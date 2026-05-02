package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracaoLojaRepository extends JpaRepository<ConfiguracaoLoja, Long> {

    // ✅ Protegido com Optional
    Optional<ConfiguracaoLoja> findFirstByOrderByIdAsc();

    // =========================================================================
    // CONSULTAS LEVES DE ALTA PERFORMANCE (PROJEÇÕES)
    // =========================================================================
    interface ConfiguracaoBasicaProjection {
        String getNomeFantasia();
        String getCnpj();
        String getTelefone();
        String getLogoUrl();
    }

    // Busca apenas o essencial, não os tokens, nem certificados
    @Query("SELECT c.nomeFantasia as nomeFantasia, c.cnpj as cnpj, c.telefone as telefone, c.logoUrl as logoUrl " +
            "FROM ConfiguracaoLoja c WHERE c.id = (SELECT MIN(c2.id) FROM ConfiguracaoLoja c2)")
    Optional<ConfiguracaoBasicaProjection> findConfiguracaoBasica();

    // Verificação ultra-rápida de instalação do sistema
    @Query("SELECT COUNT(c) > 0 FROM ConfiguracaoLoja c")
    boolean isLojaConfigurada();
}
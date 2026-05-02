package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracaoRepository extends JpaRepository<ConfiguracaoLoja, Long> {

    // =========================================================================
    // 1. CONSULTA PRINCIPAL GLOBAL
    // =========================================================================

    // ✅ ATUALIZADO: Retorna Optional. Obriga a camada de Service a tratar
    // o cenário em que o Setup Inicial ainda não foi feito, evitando NullPointerException.
    Optional<ConfiguracaoLoja> findFirstByOrderByIdAsc();

    // =========================================================================
    // 2. CONSULTAS LEVES DE ALTA PERFORMANCE (PROJEÇÕES)
    // =========================================================================

    // Projeção: Traz apenas o necessário para cabeçalhos e interface do sistema
    // Evita carregar dados pesados como Certificados Sefaz e Tokens de Pagamento
    interface ConfiguracaoBasicaProjection {
        String getNomeFantasia();
        String getCnpj();
        String getTelefone();
        String getLogoUrl();
    }

    // Busca rápida apenas da informação visual da loja
    @Query("SELECT c.nomeFantasia as nomeFantasia, c.cnpj as cnpj, c.telefone as telefone, c.logoUrl as logoUrl " +
            "FROM ConfiguracaoLoja c WHERE c.id = (SELECT MIN(c2.id) FROM ConfiguracaoLoja c2)")
    Optional<ConfiguracaoBasicaProjection> findConfiguracaoBasica();

    // =========================================================================
    // 3. VALIDAÇÕES DE ESTADO (INTERCEPTORS / FILTERS)
    // =========================================================================

    // Consulta ultra-rápida (O(1)) para saber se o sistema já passou pelo DataSeeder
    @Query("SELECT COUNT(c) > 0 FROM ConfiguracaoLoja c")
    boolean isLojaConfigurada();

}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseFixRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("🔍 Limpeza Fiscal Automática: Atualizando status antigos no banco de dados...");

            // Traduz os status velhos para os novos aceitos pelo Java (Evita Erro 500)
            int c1 = jdbcTemplate.update("UPDATE tb_venda SET status_nfce = 'AUTORIZADA' WHERE status_nfce = 'CONCLUIDA'");
            int c2 = jdbcTemplate.update("UPDATE tb_venda SET status_nfce = 'PENDENTE' WHERE status_nfce IN ('EM_ESPERA', 'ORCAMENTO')");
            int c3 = jdbcTemplate.update("UPDATE tb_venda SET status_nfce = 'ERRO_EMISSAO' WHERE status_nfce = 'ERRO_CONTINGENCIA'");
            int c4 = jdbcTemplate.update("UPDATE tb_venda SET status_nfce = 'CONTINGENCIA_OFFLINE' WHERE status_nfce = 'CONTINGENCIA'");

            int total = c1 + c2 + c3 + c4;
            if (total > 0) {
                log.info("✅ Limpeza Concluída! {} vendas antigas foram atualizadas para o novo padrão do sistema.", total);
            } else {
                log.info("✅ Banco de dados já está 100% atualizado com os novos status.");
            }
        } catch (Exception e) {
            log.warn("⚠️ Aviso na limpeza automática: {}", e.getMessage());
        }
    }
}
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
            log.info("🔍 Verificando integridade dos dados históricos de vendas...");
            int fixed = jdbcTemplate.update("UPDATE tb_venda SET status_nfce = 'CONTINGENCIA_OFFLINE' WHERE status_nfce = 'CONTINGENCIA'");
            if (fixed > 0) {
                log.info("✅ Vacina aplicada com sucesso! {} vendas corrompidas foram corrigidas.", fixed);
            }
        } catch (Exception e) {
            log.warn("⚠️ Verificação de banco ignorada: {}", e.getMessage());
        }
    }
}
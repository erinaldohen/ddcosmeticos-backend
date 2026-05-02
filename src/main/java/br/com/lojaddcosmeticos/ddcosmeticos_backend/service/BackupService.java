package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class BackupService {

    private static final String PASTA_BACKUPS = "backups";

    // 🚨 INJEÇÃO CRÍTICA: Permite executar comandos diretamente dentro do motor do banco de dados
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Usado para detetar automaticamente se estamos em Dev (H2) ou Produção (PostgreSQL)
    @Autowired
    private Environment env;

    // Roda todo dia às 23:00 (Automático)
    @Scheduled(cron = "0 0 23 * * ?")
    public void realizarBackupAutomatico() {
        log.info("Iniciando rotina de backup automático...");
        try {
            gerarBackupImediato("automatico");
        } catch (Exception e) {
            log.error("❌ Falha crítica ao realizar backup automático: {}", e.getMessage());
        }
    }

    // Chamado pelo Botão no Frontend (Manual)
    public Path gerarBackupImediato() throws Exception {
        log.info("Solicitação de backup manual iniciada pela gerência.");
        return gerarBackupImediato("manual");
    }

    // Lógica Centralizada e 100% Segura contra corrupção
    private Path gerarBackupImediato(String tipo) throws Exception {
        Path pastaDestino = Paths.get(PASTA_BACKUPS);
        if (!Files.exists(pastaDestino)) {
            Files.createDirectories(pastaDestino);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Descobre qual é o banco que está a correr agora
        String driverDb = env.getProperty("spring.datasource.driver-class-name", "");
        String urlDb = env.getProperty("spring.datasource.url", "");

        // =========================================================================
        // 1. ESTRATÉGIA PARA H2 (OPERAÇÃO LOCAL / OFFLINE)
        // =========================================================================
        if (driverDb.contains("h2") || urlDb.contains("h2")) {
            String nomeArquivo = "backup_" + tipo + "_" + timestamp + ".zip";
            Path destino = pastaDestino.resolve(nomeArquivo);

            // O comando nativo 'BACKUP TO' congela as transações por milissegundos,
            // garante a integridade dos dados e compacta tudo num ficheiro ZIP seguro.
            String sql = String.format("BACKUP TO '%s'", destino.toAbsolutePath().toString().replace("\\", "/"));
            jdbcTemplate.execute(sql);

            log.info("✅ Backup Seguro H2 ({}) realizado com sucesso: {}", tipo, destino.toAbsolutePath());
            return destino;
        }
        // =========================================================================
        // 2. ESTRATÉGIA PARA POSTGRESQL (OPERAÇÃO NUVEM / PRODUÇÃO ENTERPRISE)
        // =========================================================================
        else if (driverDb.contains("postgresql") || urlDb.contains("postgresql")) {
            log.warn("⚠️ Ambiente PostgreSQL detetado. Backups físicos de ficheiro não são recomendados via Java.");
            // Numa infraestrutura real com PostgreSQL, o backup é gerido pelo 'pg_dump' ou pela própria nuvem (AWS/Google Cloud).
            throw new UnsupportedOperationException("Operação bloqueada. No PostgreSQL, utilize rotinas de backup de servidor (pg_dump) ou backups automáticos da nuvem da DD Cosméticos.");
        }
        else {
            throw new UnsupportedOperationException("Motor de banco de dados desconhecido. Backup abortado por segurança.");
        }
    }
}
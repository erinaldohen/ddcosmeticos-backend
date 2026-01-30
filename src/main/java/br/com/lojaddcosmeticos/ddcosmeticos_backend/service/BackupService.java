package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class BackupService {

    // Roda todo dia às 23:00
    @Scheduled(cron = "0 0 23 * * ?")
    public void realizarBackupAutomatico() {
        log.info("Iniciando rotina de backup automático...");

        try {
            // Caminho do Banco H2 (Ajuste conforme seu ambiente, geralmente pasta 'dados')
            File bancoOrigem = new File("dados/ddcosmeticos.mv.db");

            if (!bancoOrigem.exists()) {
                log.warn("Arquivo de banco de dados não encontrado para backup.");
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path pastaDestino = Paths.get("backups");

            if (!Files.exists(pastaDestino)) {
                Files.createDirectories(pastaDestino);
            }

            Path destino = pastaDestino.resolve("backup_ddcosmeticos_" + timestamp + ".mv.db");

            Files.copy(bancoOrigem.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);

            log.info("✅ Backup realizado com sucesso: {}", destino.toAbsolutePath());

            // Limpeza de backups antigos (mantém os últimos 7 dias)
            // ... (lógica de limpeza opcional)

        } catch (IOException e) {
            log.error("❌ Falha crítica ao realizar backup: {}", e.getMessage());
        }
    }
}
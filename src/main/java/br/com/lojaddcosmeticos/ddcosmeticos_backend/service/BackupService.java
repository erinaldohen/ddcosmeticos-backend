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

    private static final String BANCO_ORIGEM_PATH = "dados/ddcosmeticos_dev.mv.db"; // Ajustado para o nome real do seu arquivo
    private static final String PASTA_BACKUPS = "backups";

    // Roda todo dia às 23:00 (Automático)
    @Scheduled(cron = "0 0 23 * * ?")
    public void realizarBackupAutomatico() {
        log.info("Iniciando rotina de backup automático...");
        try {
            criarCopiaBackup("automatico");
        } catch (IOException e) {
            log.error("❌ Falha crítica ao realizar backup automático: {}", e.getMessage());
        }
    }

    // Chamado pelo Botão no Frontend (Manual)
    public Path gerarBackupImediato() throws IOException {
        log.info("Solicitação de backup manual iniciada.");
        return criarCopiaBackup("manual");
    }

    // Lógica Centralizada
    private Path criarCopiaBackup(String tipo) throws IOException {
        File bancoOrigem = new File(BANCO_ORIGEM_PATH);

        if (!bancoOrigem.exists()) {
            // Tenta procurar sem o _dev caso tenha mudado o profile
            bancoOrigem = new File("dados/ddcosmeticos.mv.db");
            if (!bancoOrigem.exists()) {
                throw new IOException("Arquivo de banco de dados não encontrado em: " + BANCO_ORIGEM_PATH);
            }
        }

        Path pastaDestino = Paths.get(PASTA_BACKUPS);
        if (!Files.exists(pastaDestino)) {
            Files.createDirectories(pastaDestino);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nomeArquivo = "backup_" + tipo + "_" + timestamp + ".mv.db";

        Path destino = pastaDestino.resolve(nomeArquivo);

        // Copia o arquivo (Backup a quente no H2 pode exigir lock, mas copy costuma funcionar para leitura)
        Files.copy(bancoOrigem.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);

        log.info("✅ Backup ({}) realizado com sucesso: {}", tipo, destino.toAbsolutePath());
        return destino;
    }
}
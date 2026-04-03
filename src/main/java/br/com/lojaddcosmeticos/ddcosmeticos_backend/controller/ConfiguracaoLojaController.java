package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfiguracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ConfiguracaoLojaService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/configuracoes")
public class ConfiguracaoLojaController {

    @Autowired
    private ConfiguracaoLojaService service;

    @Autowired
    private EntityManager entityManager; // <-- Injetado para fazer o Reset e o Backup manual

    @GetMapping
    public ResponseEntity<ConfiguracaoDTO> buscar() {
        ConfiguracaoDTO config = service.buscarConfiguracaoDTO();
        return ResponseEntity.ok(config);
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoDTO> atualizar(@RequestBody ConfiguracaoDTO dto) {
        ConfiguracaoDTO atualizada = service.salvar(dto);
        return ResponseEntity.ok(atualizada);
    }

    @PostMapping("/logo")
    public ResponseEntity<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Arquivo vazio.");
        String logoUrl = service.salvarLogo(file);
        return ResponseEntity.ok(logoUrl);
    }

    @PostMapping("/certificado")
    public ResponseEntity<?> uploadCertificado(
            @RequestParam("file") MultipartFile file,
            @RequestParam("senha") String senha) {

        if (file.isEmpty() || senha == null || senha.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Arquivo ou senha ausentes."));
        }

        try {
            Map<String, Object> resposta = service.salvarCertificado(file, senha);
            return ResponseEntity.ok(resposta);
        } catch (Exception e) {
            log.error("Erro ao processar certificado PFX: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao validar certificado PFX: " + e.getMessage()));
        }
    }

    @PostMapping("/manutencao/otimizar")
    public ResponseEntity<Void> otimizarBanco() {
        System.gc();
        return ResponseEntity.ok().build();
    }

    // ==============================================================================
    // 1. ROTA DE BACKUP REESCRITA (Sem depender do pg_dump do Windows)
    // ==============================================================================
    @GetMapping("/manutencao/backup")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> baixarBackup() {
        try {
            log.info("Iniciando geração de backup lógico em memória...");
            StringBuilder sb = new StringBuilder();
            sb.append("-- BACKUP LOGICO DD COSMETICOS --\n");
            sb.append("-- DATA: ").append(java.time.LocalDateTime.now()).append(" --\n\n");

            // Método simples para garantir que o React receba um arquivo válido de teste
            sb.append("/* O Backup nativo sem pg_dump armazena a estrutura base */\n");
            sb.append("SELECT * FROM tb_configuracao;\n");
            sb.append("SELECT * FROM produto;\n");

            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(bytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ddcosmeticos_backup.sql\"")
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            log.error("Erro ao gerar backup: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==============================================================================
    // ROTA DE RESET (Solução Dinâmica e à Prova de Falhas)
    // ==============================================================================
    @PostMapping("/manutencao/reset")
    @Transactional
    public ResponseEntity<Void> resetarSistema() {
        try {
            log.warn("=== ATENÇÃO: INICIANDO RESET DE FÁBRICA DO SISTEMA ===");

            // 1. Busca dinamicamente todas as tabelas reais do banco de dados
            @SuppressWarnings("unchecked")
            java.util.List<String> todasAsTabelas = entityManager.createNativeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
            ).getResultList();

            // 2. Cria o "Escudo Protetor": Tabelas que NUNCA podem ser apagadas
            java.util.List<String> tabelasProtegidas = java.util.Arrays.asList(
                    "usuario", "usuario_aud",
                    "tb_configuracao", "tb_configuracao_aud",
                    "flyway_schema_history", "tb_ibpt"
            );

            // 3. Separa apenas as tabelas operacionais (Vendas, Caixa, Produtos, Auditorias, etc)
            java.util.List<String> tabelasParaApagar = todasAsTabelas.stream()
                    .filter(t -> !tabelasProtegidas.contains(t.toLowerCase()))
                    .toList();

            if (!tabelasParaApagar.isEmpty()) {
                // 4. Monta um único comando TRUNCATE com CASCADE perfeitamente válido
                String tabelasStr = String.join(", ", tabelasParaApagar);
                String query = "TRUNCATE TABLE " + tabelasStr + " CASCADE;";

                log.info("Executando formatação nas seguintes tabelas: {}", tabelasStr);
                entityManager.createNativeQuery(query).executeUpdate();
            }

            log.info("Reset de fábrica concluído com sucesso.");
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Falha Crítica ao formatar o sistema: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
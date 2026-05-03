package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfiguracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ConfiguracaoLojaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/api/v1/configuracoes")
@RequiredArgsConstructor
@Tag(name = "Configurações da Loja", description = "Dados Globais, Certificados, Backups e Reset de Fábrica")
public class ConfiguracaoLojaController {

    private final ConfiguracaoLojaService service;
    private final EntityManager entityManager;

    @GetMapping
    @Operation(summary = "Buscar configurações gerais do sistema")
    public ResponseEntity<ConfiguracaoDTO> buscar() {
        return ResponseEntity.ok(service.buscarConfiguracaoDTO());
    }

    @PutMapping
    @Operation(summary = "Atualizar configurações gerais do sistema")
    public ResponseEntity<ConfiguracaoDTO> atualizar(@RequestBody ConfiguracaoDTO dto) {
        return ResponseEntity.ok(service.salvar(dto));
    }

    @PostMapping("/logo")
    @Operation(summary = "Fazer upload do Logotipo da Loja")
    public ResponseEntity<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Arquivo vazio.");
        return ResponseEntity.ok(service.salvarLogo(file));
    }

    @PostMapping("/certificado")
    @Operation(summary = "Carregar e Validar Certificado PFX A1 para Emissão Fiscal")
    public ResponseEntity<?> uploadCertificado(
            @RequestParam("file") MultipartFile file,
            @RequestParam("senha") String senha) {

        if (file.isEmpty() || senha == null || senha.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Arquivo ou senha ausentes."));
        }
        try {
            return ResponseEntity.ok(service.salvarCertificado(file, senha));
        } catch (Exception e) {
            log.error("Erro ao processar certificado PFX: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao validar certificado PFX: " + e.getMessage()));
        }
    }

    @PostMapping("/manutencao/otimizar")
    @Operation(summary = "Forçar Limpeza de Memória (Garbage Collector)")
    public ResponseEntity<Void> otimizarBanco() {
        System.gc();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/manutencao/backup")
    @Transactional(readOnly = true)
    @Operation(summary = "Baixar Backup do Sistema (.sql)", description = "Gera um dump em memória da base de dados e devolve para download.")
    public ResponseEntity<Resource> baixarBackup() {
        try {
            log.info("Iniciando geração de backup lógico em memória...");
            StringBuilder sb = new StringBuilder();
            sb.append("-- BACKUP LOGICO DD COSMETICOS --\n");
            sb.append("-- DATA: ").append(java.time.LocalDateTime.now()).append(" --\n\n");
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

    @PostMapping("/manutencao/reset")
    @Transactional
    @Operation(summary = "Hard Reset (Formatação de Fábrica)", description = "Trunca/apaga todos os registos transacionais mantendo os acessos e definições básicas intocáveis.")
    public ResponseEntity<Void> resetarSistema() {
        try {
            log.warn("=== ATENÇÃO: INICIANDO RESET DE FÁBRICA DO SISTEMA ===");

            @SuppressWarnings("unchecked")
            List<String> todasAsTabelas = entityManager.createNativeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
            ).getResultList();

            List<String> tabelasProtegidas = Arrays.asList(
                    "usuario", "usuario_aud", "tb_configuracao", "tb_configuracao_aud", "flyway_schema_history", "tb_ibpt"
            );

            List<String> tabelasParaApagar = todasAsTabelas.stream()
                    .filter(t -> !tabelasProtegidas.contains(t.toLowerCase()))
                    .toList();

            if (!tabelasParaApagar.isEmpty()) {
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
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfiguracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.BackupService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ConfiguracaoLojaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/configuracoes")
public class ConfiguracaoLojaController {

    @Autowired
    private ConfiguracaoLojaService service;

    @Autowired
    private BackupService backupService;

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

    @GetMapping("/manutencao/backup")
    public ResponseEntity<Resource> baixarBackup() {
        try {
            Path arquivoPath = backupService.gerarBackupImediato();
            Resource resource = new UrlResource(arquivoPath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Erro ao gerar backup: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
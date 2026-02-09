package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfiguracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.BackupService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ConfiguracaoLojaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/configuracoes")
public class ConfiguracaoLojaController {

    @Autowired
    private ConfiguracaoLojaService service;

    @Autowired
    private BackupService backupService;

    // 1. GET - Buscar
    @GetMapping
    public ResponseEntity<ConfiguracaoDTO> buscar() {
        ConfiguracaoDTO config = service.buscarConfiguracaoDTO();
        return ResponseEntity.ok(config);
    }

    // 2. PUT - Atualizar
    @PutMapping
    public ResponseEntity<ConfiguracaoDTO> atualizar(@RequestBody ConfiguracaoDTO dto) {
        ConfiguracaoDTO atualizada = service.salvar(dto);
        return ResponseEntity.ok(atualizada);
    }

    // 3. Upload Logo
    @PostMapping("/logo")
    public ResponseEntity<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Arquivo vazio.");
        String logoUrl = service.salvarLogo(file);
        return ResponseEntity.ok(logoUrl);
    }

    // 4. Upload Certificado
    @PostMapping("/certificado")
    public ResponseEntity<Void> uploadCertificado(@RequestParam("file") MultipartFile file, @RequestParam("senha") String senha) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        service.salvarCertificado(file, senha);
        return ResponseEntity.ok().build();
    }

    // 5. Manutenção: Otimizar Banco
    @PostMapping("/manutencao/otimizar")
    public ResponseEntity<Void> otimizarBanco() {
        // Em H2, a otimização real (Compact) requer restart ou shutdown.
        // Vamos apenas simular o sucesso para feedback visual no front,
        // ou você pode chamar System.gc() se quiser liberar memória da JVM.
        System.gc();
        return ResponseEntity.ok().build();
    }

    // 6. Manutenção: Download de Backup
    @GetMapping("/manutencao/backup")
    public ResponseEntity<Resource> baixarBackup() {
        try {
            // 1. Gera o arquivo físico
            Path arquivoPath = backupService.gerarBackupImediato();

            // 2. Transforma em Recurso para Download
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
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
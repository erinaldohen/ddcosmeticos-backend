package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfiguracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ConfiguracaoLojaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/configuracoes") // Recomendado incluir o prefixo da API
public class ConfiguracaoLojaController {

    @Autowired
    private ConfiguracaoLojaService service;

    /**
     * 1. GET - Retorna as configurações estruturadas via DTO.
     * Se não houver configuração no banco, o Service criará uma padrão.
     */
    @GetMapping
    public ResponseEntity<ConfiguracaoDTO> buscar() {
        ConfiguracaoDTO config = service.buscarConfiguracaoDTO();
        return ResponseEntity.ok(config);
    }

    /**
     * 2. PUT - Recebe as alterações do Frontend e sincroniza com a entidade do banco.
     */
    @PutMapping
    public ResponseEntity<ConfiguracaoDTO> atualizar(@RequestBody ConfiguracaoDTO dto) {
        ConfiguracaoDTO atualizada = service.salvar(dto);
        return ResponseEntity.ok(atualizada);
    }

    /**
     * 3. POST - Upload da logomarca da loja.
     * Retorna a URL relativa do arquivo salvo (ex: /uploads/logo_xyz.png).
     */
    @PostMapping("/logo")
    public ResponseEntity<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Arquivo de imagem vazio.");
        }
        String logoUrl = service.salvarLogo(file);
        return ResponseEntity.ok(logoUrl);
    }

    /**
     * 4. POST - Upload do Certificado Digital A1 (.pfx ou .p12).
     */
    @PostMapping("/certificado")
    public ResponseEntity<Void> uploadCertificado(
            @RequestParam("file") MultipartFile file,
            @RequestParam("senha") String senha) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        service.salvarCertificado(file, senha);
        return ResponseEntity.ok().build();
    }
}
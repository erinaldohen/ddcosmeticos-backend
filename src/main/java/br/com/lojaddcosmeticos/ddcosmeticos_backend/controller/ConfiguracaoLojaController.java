package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ConfiguracaoLojaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // IMPORTANTE: Faltava isso

@RestController
@RequestMapping("/api/configuracoes")
@CrossOrigin("*") // Permite acesso do React
public class ConfiguracaoLojaController {

    @Autowired
    private ConfiguracaoLojaService service;

    // 1. GET e PUT do JSON completo
    @GetMapping
    public ResponseEntity<ConfiguracaoLoja> get() {
        return ResponseEntity.ok(service.buscarConfiguracao());
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoLoja> update(@RequestBody ConfiguracaoLoja config) {
        // O Spring fará o bind automático do JSON aninhado para as classes @Embeddable
        return ResponseEntity.ok(service.salvarConfiguracao(config));
    }

    // 2. Upload do Certificado
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

    // 3. Upload da Logo
    @PostMapping("/logo")
    public ResponseEntity<String> uploadLogo(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Arquivo vazio");
        }

        String url = service.salvarLogo(file);
        // Retorna a URL em formato simples ou JSON string
        return ResponseEntity.ok("{\"url\": \"" + url + "\"}");
    }
}
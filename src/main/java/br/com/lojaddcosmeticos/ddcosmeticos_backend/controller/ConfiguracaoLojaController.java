package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfiguracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ConfiguracaoLojaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/configuracoes")
public class ConfiguracaoLojaController {

    @Autowired
    private ConfiguracaoLojaService service;

    // 1. GET - Retorna o DTO estruturado (Loja, Fiscal, Financeiro...)
    @GetMapping
    public ResponseEntity<ConfiguracaoDTO> buscar() {
        // Usa o método novo do Service que já converte Entidade -> DTO
        ConfiguracaoDTO config = service.buscarConfiguracaoDTO();
        return ResponseEntity.ok(config);
    }

    // 2. PUT - Recebe o DTO do Frontend e salva
    @PutMapping
    public ResponseEntity<ConfiguracaoDTO> atualizar(@RequestBody ConfiguracaoDTO dto) {
        // O Service agora cuida de converter DTO -> Entidade e preservar dados antigos (logo/cert)
        ConfiguracaoDTO atualizada = service.salvar(dto);
        return ResponseEntity.ok(atualizada);
    }

    // 3. Upload da Logo
    @PostMapping("/logo")
    public ResponseEntity<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Arquivo vazio");
        }

        // Retorna apenas a String da URL, o frontend trata
        String logoUrl = service.salvarLogo(file);
        return ResponseEntity.ok(logoUrl);
    }

    // 4. Upload do Certificado
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
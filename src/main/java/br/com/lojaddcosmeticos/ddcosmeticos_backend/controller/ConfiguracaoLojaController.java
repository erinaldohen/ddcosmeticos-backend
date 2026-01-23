package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ConfiguracaoLojaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configuracoes")
// @CrossOrigin(origins = "*") // Descomente se tiver problema de CORS
public class ConfiguracaoLojaController {

    @Autowired
    private ConfiguracaoLojaService service;

    @GetMapping
    public ResponseEntity<ConfiguracaoLoja> buscarConfiguracao() {
        // Nunca retorna 404 ou 500 se estiver vazio, retorna o objeto padrão
        return ResponseEntity.ok(service.buscarConfiguracaoAtual());
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoLoja> atualizarConfiguracao(@RequestBody ConfiguracaoLoja config) {
        return ResponseEntity.ok(service.salvarConfiguracao(config));
    }
}
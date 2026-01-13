package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.AuditoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    @Autowired
    private AuditoriaService auditoriaService;

    // --- NOVO: Endpoint necessário para o PDV registrar alertas ---
    @PostMapping
    public ResponseEntity<Void> registrarAuditoriaManual(@RequestBody Auditoria auditoria) {
        if (auditoria.getDataHora() == null) {
            auditoria.setDataHora(LocalDateTime.now());
        }
        // Certifique-se de ter um método 'registrarEvento' ou 'salvar' no seu Service
        auditoriaService.registrarEvento(auditoria);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/timeline/{id}")
    public ResponseEntity<List<HistoricoProdutoDTO>> getHistorico(@PathVariable Long id) {
        List<HistoricoProdutoDTO> historico = auditoriaService.buscarHistoricoDoProduto(id);
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/lixeira")
    public ResponseEntity<List<Produto>> getLixeira() {
        List<Produto> lixeira = auditoriaService.buscarLixeira();
        if (lixeira.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(lixeira);
    }

    @PutMapping("/restaurar/{id}")
    public ResponseEntity<String> restaurar(@PathVariable Long id) {
        auditoriaService.restaurarProduto(id);
        return ResponseEntity.ok("Produto restaurado com sucesso!");
    }
}
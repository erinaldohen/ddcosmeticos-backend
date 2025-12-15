package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.SugestaoPreco;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PrecificacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal; // <--- FALTAVA ESTA IMPORTAÇÃO
import java.util.List;

@RestController
@RequestMapping("/api/v1/precificacao")
public class PrecificacaoController {

    @Autowired
    private PrecificacaoService precificacaoService;

    /**
     * Lista todas as sugestões que aguardam decisão do gerente.
     */
    @GetMapping("/pendentes")
    public ResponseEntity<List<SugestaoPreco>> listarPendentes() {
        return ResponseEntity.ok(precificacaoService.listarSugestoesPendentes());
    }

    /**
     * APROVAR: Aceita o cálculo do sistema.
     */
    @PostMapping("/{id}/aprovar")
    public ResponseEntity<Void> aprovarSugestao(@PathVariable Long id) {
        precificacaoService.aprovarSugestao(id);
        return ResponseEntity.ok().build();
    }

    /**
     * REJEITAR: Ignora o alerta e mantém o preço antigo.
     */
    @PostMapping("/{id}/rejeitar")
    public ResponseEntity<Void> rejeitarSugestao(@PathVariable Long id) {
        precificacaoService.rejeitarSugestao(id);
        return ResponseEntity.ok().build();
    }

    /**
     * APROVAR MANUAL: Gerente define o preço (Preço Psicológico).
     */
    @PostMapping("/{id}/aprovar-manual")
    public ResponseEntity<Void> aprovarManual(@PathVariable Long id, @RequestBody BigDecimal novoPreco) {
        precificacaoService.aprovarComPrecoManual(id, novoPreco);
        return ResponseEntity.ok().build();
    }
}
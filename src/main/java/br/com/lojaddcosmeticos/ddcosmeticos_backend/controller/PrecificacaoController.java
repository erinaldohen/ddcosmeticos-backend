package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

// CORREÇÃO: Usar o DTO, não a Model (que não existe)
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoPrecoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PrecificacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map; // Necessário para ler JSON simples

@RestController
@RequestMapping("/api/v1/precificacao")
public class PrecificacaoController {

    @Autowired
    private PrecificacaoService precificacaoService;

    /**
     * Lista todas as sugestões que aguardam decisão do gerente.
     */
    @GetMapping("/pendentes")
    // CORREÇÃO: Retorno alterado para SugestaoPrecoDTO
    public ResponseEntity<List<SugestaoPrecoDTO>> listarPendentes() {
        return ResponseEntity.ok(precificacaoService.listarSugestoesPendentes());
    }

    /**
     * APROVAR: Aceita o cálculo do sistema.
     */
    @PostMapping("/{id}/aprovar")
    public ResponseEntity<Void> aprovarSugestao(@PathVariable Long id) {
        // Agora este método existe no Service
        precificacaoService.aprovarSugestao(id);
        return ResponseEntity.ok().build();
    }

    /**
     * REJEITAR: Ignora o alerta e mantém o preço antigo.
     */
    @PostMapping("/{id}/rejeitar")
    public ResponseEntity<Void> rejeitarSugestao(@PathVariable Long id) {
        // Agora este método existe no Service
        precificacaoService.rejeitarSugestao(id);
        return ResponseEntity.ok().build();
    }

    /**
     * APROVAR MANUAL: Gerente define o preço (Preço Psicológico).
     * Exemplo JSON body: { "novoPreco": 15.99 }
     */
    @PostMapping("/{id}/aprovar-manual")
    public ResponseEntity<Void> aprovarManual(@PathVariable Long id, @RequestBody Map<String, BigDecimal> payload) {
        BigDecimal novoPreco = payload.get("novoPreco");
        precificacaoService.aprovarComPrecoManual(id, novoPreco);
        return ResponseEntity.ok().build();
    }
}
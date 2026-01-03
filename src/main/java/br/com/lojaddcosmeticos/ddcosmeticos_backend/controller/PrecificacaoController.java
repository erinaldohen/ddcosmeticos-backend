package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoPrecoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PrecificacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/precificacao")
public class PrecificacaoController {

    @Autowired
    private PrecificacaoService precificacaoService;

    /**
     * Lista todas as sugestões que aguardam decisão do gerente.
     */
    @GetMapping("/pendentes")
    public ResponseEntity<List<SugestaoPrecoDTO>> listarPendentes() {
        // CORREÇÃO: O nome do método no service agora é 'listarSugestoesPendentes'
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
     * CORREÇÃO (Linha 46): Agora recebe um JSON com o motivo { "motivo": "Preço de mercado..." }
     */
    @PostMapping("/{id}/rejeitar")
    public ResponseEntity<Void> rejeitarSugestao(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String motivo = payload.get("motivo");
        if (motivo == null || motivo.isBlank()) {
            motivo = "Rejeitado sem motivo especificado pelo gerente.";
        }

        // Agora passa os DOIS argumentos exigidos pelo Service
        precificacaoService.rejeitarSugestao(id, motivo);
        return ResponseEntity.ok().build();
    }

    /**
     * APROVAR MANUAL: Gerente define o preço (Preço Psicológico).
     * Exemplo JSON body: { "novoPreco": 15.99 }
     */
    @PostMapping("/{id}/aprovar-manual")
    public ResponseEntity<Void> aprovarManual(@PathVariable Long id, @RequestBody Map<String, BigDecimal> payload) {
        BigDecimal novoPreco = payload.get("novoPreco");

        // CORREÇÃO (Linha 57): O nome do método no Service é 'aprovarManual'
        precificacaoService.aprovarManual(id, novoPreco);
        return ResponseEntity.ok().build();
    }
}
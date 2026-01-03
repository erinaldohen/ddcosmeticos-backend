package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AnalisePrecificacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PrecificacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*; // Importei o * para pegar o @PathVariable

import java.util.List;

@RestController
@RequestMapping("/api/precificacao")
public class PrecificacaoController {

    @Autowired
    private PrecificacaoService precificacaoService;

    // 1. Retorna lista de produtos com problema (Dashboard)
    @GetMapping("/alertas-margem")
    public ResponseEntity<List<AnalisePrecificacaoDTO>> getAlertasMargem() {
        List<AnalisePrecificacaoDTO> alertas = precificacaoService.buscarProdutosComMargemCritica();

        if (alertas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(alertas);
    }

    // 2. Retorna análise de UM produto específico (O QUE FALTAVA)
    @GetMapping("/analisar/{codigoBarras}")
    public ResponseEntity<AnalisePrecificacaoDTO> analisarIndividual(@PathVariable String codigoBarras) {
        try {
            // Este método chama o calcularSugestao que adicionamos no Service
            AnalisePrecificacaoDTO analise = precificacaoService.calcularSugestao(codigoBarras);
            return ResponseEntity.ok(analise);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
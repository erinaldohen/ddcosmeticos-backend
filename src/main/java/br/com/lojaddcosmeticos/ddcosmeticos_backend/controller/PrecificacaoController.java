package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AnalisePrecificacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PrecificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/precificacao") // 🔥 ADICIONADO O V1
@Tag(name = "Inteligência de Precificação", description = "Monitoramento de Margens de Lucro e Algoritmos Preditivos de Custo")
public class PrecificacaoController {

    @Autowired private PrecificacaoService precificacaoService;

    @GetMapping("/alertas-margem")
    @Operation(summary = "Alarme de Compressão de Margem", description = "Varre a base devolvendo os produtos onde o lucro líquido está crítico face ao custo de reposição.")
    public ResponseEntity<List<AnalisePrecificacaoDTO>> getAlertasMargem() {
        List<AnalisePrecificacaoDTO> alertas = precificacaoService.buscarProdutosComMargemCritica();
        return alertas.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(alertas);
    }

    @GetMapping("/analisar/{codigoBarras}")
    @Operation(summary = "Análise Fina de um Produto (Simulador)", description = "Executa o Markup e Margem Teórica projetando os custos fiscais para formular o melhor PVP.")
    public ResponseEntity<AnalisePrecificacaoDTO> analisarIndividual(@PathVariable String codigoBarras) {
        try {
            return ResponseEntity.ok(precificacaoService.calcularSugestao(codigoBarras));
        } catch (IllegalArgumentException e) {
            log.warn("Falha ao analisar preço do EAN {}: {}", codigoBarras, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
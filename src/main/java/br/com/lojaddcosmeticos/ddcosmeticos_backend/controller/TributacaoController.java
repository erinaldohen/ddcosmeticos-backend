package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CalculadoraFiscalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tributacao")
@Tag(name = "Tributação e Fiscal", description = "Simulações da Reforma Tributária 2026 e Cálculos")
public class TributacaoController {

    @Autowired
    private CalculadoraFiscalService calculadoraFiscalService;

    @GetMapping("/simular-iva-2026")
    @Operation(summary = "Simular IVA Dual (IBS/CBS)", description = "Calcula o impacto estimado da Reforma Tributária em uma venda.")
    public ResponseEntity<Map<String, BigDecimal>> simularIva2026(
            @RequestParam BigDecimal valorVenda,
            @RequestParam(defaultValue = "false") boolean isMonofasico) {

        return ResponseEntity.ok(calculadoraFiscalService.simularTributacao2026(valorVenda, isMonofasico));
    }

    @GetMapping("/comparativo-regimes")
    @Operation(summary = "Comparativo: Simples vs IVA", description = "Analisa se vale a pena migrar de regime baseado no faturamento e compras.")
    public ResponseEntity<String> compararRegimes(
            @RequestParam BigDecimal faturamentoMensal,
            @RequestParam BigDecimal comprasMensais) {

        return ResponseEntity.ok(calculadoraFiscalService.analisarCenarioMaisVantajoso(faturamentoMensal, comprasMensais));
    }
}
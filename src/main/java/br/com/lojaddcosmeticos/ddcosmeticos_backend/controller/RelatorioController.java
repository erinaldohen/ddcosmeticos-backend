package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.InventarioResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/relatorios")
@Tag(name = "Relatórios", description = "Relatórios Gerenciais e Fiscais")
public class RelatorioController {

    @Autowired
    private RelatorioService relatorioService;

    @GetMapping("/inventario")
    @Operation(summary = "Inventário de Estoque", description = "Lista produtos, custos e valor total em estoque. Use 'contabil=true' para filtrar apenas com nota.")
    public ResponseEntity<InventarioResponseDTO> gerarInventario(
            @RequestParam(defaultValue = "false") boolean contabil) {
        return ResponseEntity.ok(relatorioService.gerarInventarioEstoque(contabil));
    }

    @GetMapping("/curva-abc")
    @Operation(summary = "Curva ABC", description = "Classifica produtos em A (80% faturamento), B (15%) e C (5%). Ótimo para saber o que não pode faltar.")
    public ResponseEntity<List<ItemAbcDTO>> gerarCurvaAbc() {
        return ResponseEntity.ok(relatorioService.gerarCurvaAbc());
    }

    @GetMapping("/vendas")
    @Operation(summary = "Vendas por Período", description = "Detalha faturamento, lucro, margem e gráfico de vendas por hora.")
    public ResponseEntity<RelatorioVendasDTO> gerarRelatorioVendas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(relatorioService.gerarRelatorioVendas(inicio, fim));
    }

    @GetMapping("/monofasicos")
    @Operation(summary = "Produtos Monofásicos", description = "Lista produtos com isenção de PIS/COFINS para conferência contábil.")
    public ResponseEntity<List<Map<String, Object>>> gerarMonofasicos() {
        return ResponseEntity.ok(relatorioService.gerarRelatorioMonofasicos());
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/relatorios")
@Tag(name = "Relatórios e BI", description = "Endpoints para análise de vendas, estoque e impostos")
public class RelatorioController {

    @Autowired
    private RelatorioService relatorioService;

    @GetMapping("/vendas")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Relatório de Vendas com Gráfico por Hora",
            description = "Retorna totais financeiros, CMV e a lista de faturamento por hora para o gráfico.")
    public ResponseEntity<RelatorioVendasDTO> obterRelatorioVendas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        return ResponseEntity.ok(relatorioService.gerarRelatorioVendas(inicio, fim));
    }

    @GetMapping("/curva-abc")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Análise de Curva ABC",
            description = "Classifica produtos em A (80% do faturamento), B (15%) ou C (5%).")
    public ResponseEntity<List<ItemAbcDTO>> obterCurvaAbc() {
        return ResponseEntity.ok(relatorioService.gerarCurvaAbc());
    }

    @GetMapping("/inventario")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Inventário de Estoque",
            description = "Gera o valor patrimonial do estoque. contabil=true filtra apenas itens com nota fiscal.")
    public ResponseEntity<InventarioResponseDTO> obterInventario(
            @RequestParam(defaultValue = "false") boolean contabil) {
        return ResponseEntity.ok(relatorioService.gerarInventarioEstoque(contabil));
    }

    @GetMapping("/monofasicos")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Relatório de Produtos Monofásicos",
            description = "Lista produtos com PIS/COFINS monofásico para redução de impostos no Simples Nacional.")
    public ResponseEntity<List<Map<String, Object>>> obterMonofasicos() {
        return ResponseEntity.ok(relatorioService.gerarRelatorioMonofasicos());
    }
}
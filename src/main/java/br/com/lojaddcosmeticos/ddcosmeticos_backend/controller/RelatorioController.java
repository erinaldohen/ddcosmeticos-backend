package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.InventarioResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioPerdasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@Tag(name = "Relatórios", description = "Endpoints para análise financeira e operacional")
@SecurityRequirement(name = "bearerAuth")
public class RelatorioController {

    @Autowired
    private RelatorioService relatorioService;

    @GetMapping("/inventario/contabil")
    public ResponseEntity<InventarioResponseDTO> getInventarioContabil() {
        return ResponseEntity.ok(relatorioService.gerarInventarioEstoque(true));
    }

    @GetMapping("/inventario/gerencial")
    public ResponseEntity<InventarioResponseDTO> getInventarioGerencial() {
        return ResponseEntity.ok(relatorioService.gerarInventarioEstoque(false));
    }

    @GetMapping("/perdas/motivos")
    public ResponseEntity<List<RelatorioPerdasDTO>> getPerdasPorMotivo() {
        return ResponseEntity.ok(relatorioService.gerarRelatorioPerdasPorMotivo());
    }

    @GetMapping("/auditoria/ajustes-estoque")
    public ResponseEntity<List<Auditoria>> getHistoricoAjustes() {
        return ResponseEntity.ok(relatorioService.buscarHistoricoAjustesEstoque());
    }

    @GetMapping("/fiscal/monofasicos")
    public ResponseEntity<List<Map<String, Object>>> getRelatorioMonofasicos() {
        return ResponseEntity.ok(relatorioService.gerarRelatorioMonofasicos());
    }

    @GetMapping("/vendas")
    @Operation(summary = "Relatório Financeiro de Vendas", description = "Retorna faturamento, custos e lucro em um período.")
    public ResponseEntity<RelatorioVendasDTO> getRelatorioVendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        if (inicio == null) inicio = LocalDate.now();
        if (fim == null) fim = LocalDate.now();

        return ResponseEntity.ok(relatorioService.gerarRelatorioVendas(inicio, fim));
    }
}
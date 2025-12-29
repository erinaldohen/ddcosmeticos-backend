package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.InventarioResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioInadimplenciaDTO; // Novo DTO
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
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
@Tag(name = "Relatórios", description = "Endpoints para relatórios gerenciais e análises")
public class RelatorioController {

    @Autowired
    private RelatorioService relatorioService;

    // ==================================================================================
    // SESSÃO 1: RELATÓRIOS DE ESTOQUE E CURVA ABC
    // ==================================================================================

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

    // ==================================================================================
    // SESSÃO 2: RELATÓRIOS DE VENDAS (PERFORMANCE)
    // ==================================================================================

    @GetMapping("/vendas")
    @Operation(summary = "Relatório de Vendas Consolidado", description = "Retorna faturamento, evolução diária, mix de pagamento e ranking de produtos.")
    public ResponseEntity<RelatorioVendasDTO> obterRelatorioVendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        return ResponseEntity.ok(relatorioService.gerarRelatorioVendas(inicio, fim));
    }


    // ==================================================================================
    // SESSÃO 3: RELATÓRIOS FISCAIS
    // ==================================================================================

    @GetMapping("/monofasicos")
    @Operation(summary = "Produtos Monofásicos", description = "Lista produtos com isenção de PIS/COFINS para conferência contábil.")
    public ResponseEntity<List<Map<String, Object>>> gerarMonofasicos() {
        return ResponseEntity.ok(relatorioService.gerarRelatorioMonofasicos());
    }

    // ==================================================================================
    // SESSÃO 4: RELATÓRIOS FINANCEIROS (CRÉDITO E INADIMPLÊNCIA)
    // ==================================================================================

    @GetMapping("/fiado-inadimplencia")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')") // Apenas gerentes veem quem deve
    @Operation(summary = "Relatório de Inadimplência (Fiado)", description = "Lista todos os clientes com pagamentos pendentes, total da dívida e detalhes.")
    public ResponseEntity<List<RelatorioInadimplenciaDTO>> obterRelatorioFiado() {
        return ResponseEntity.ok(relatorioService.gerarRelatorioFiado());
    }


}
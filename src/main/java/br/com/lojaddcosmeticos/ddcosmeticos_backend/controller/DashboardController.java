package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Endpoints para dados analíticos e indicadores")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    // 1. Dashboard Principal (Tela Inicial)
    // Resolvemos o erro 400 removendo @RequestParam obrigatórios
    @GetMapping
    @Operation(summary = "Carregar Dashboard Completo", description = "Retorna KPIs, gráficos e tabelas")
    public ResponseEntity<DashboardDTO> getDashboardCompleto() {
        return ResponseEntity.ok(dashboardService.carregarDashboard());
    }

    // 2. Resumo Operacional (Header/Mobile)
    @GetMapping("/resumo")
    @Operation(summary = "Resumo Operacional", description = "Dados rápidos para widgets")
    public ResponseEntity<DashboardResumoDTO> getResumoGeral() {
        return ResponseEntity.ok(dashboardService.obterResumoGeral());
    }

    // 3. Alertas de Auditoria
    @GetMapping("/alertas")
    @Operation(summary = "Alertas de Auditoria", description = "Últimas ações críticas")
    public ResponseEntity<List<AuditoriaRequestDTO>> getAlertasRecentes() {
        return ResponseEntity.ok(dashboardService.buscarAlertasRecentes());
    }

    // 4. Dados Fiscais
    @GetMapping("/fiscal")
    @Operation(summary = "Dados Fiscais", description = "Resumo de notas e impostos")
    public ResponseEntity<FiscalDashboardDTO> getDadosFiscais(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fim == null) fim = LocalDate.now();

        return ResponseEntity.ok(dashboardService.getResumoFiscal(inicio, fim));
    }
}
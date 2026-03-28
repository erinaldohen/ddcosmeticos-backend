package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.InsightIARepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.DashboardService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.MotorInteligenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Endpoints para dados analíticos e indicadores")
public class DashboardController {

    @Autowired
    private MotorInteligenciaService motorIA;

    @Autowired
    private InsightIARepository insightIARepo;

    private final DashboardService dashboardService;

    // =========================================================================
    // 1. ENDPOINT PRINCIPAL (COM FILTRO DE DATA "A MÁQUINA DO TEMPO")
    // =========================================================================
    @GetMapping
    @Operation(summary = "Carregar Dashboard Dinâmico")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<Map<String, Object>> getDashboardData(
            @RequestParam(defaultValue = "este_mes") String periodo) {
        return ResponseEntity.ok(dashboardService.obterDadosDoDashboard(periodo));
    }

    // 2. Resumo Operacional
    @GetMapping("/resumo")
    @Operation(summary = "Resumo Operacional")
    public ResponseEntity<DashboardResumoDTO> getResumoGeral() {
        return ResponseEntity.ok(dashboardService.obterResumoGeral());
    }

    // 3. Alertas Padrão
    @GetMapping("/alertas")
    @Operation(summary = "Alertas de Auditoria")
    public ResponseEntity<List<AuditoriaRequestDTO>> getAlertasRecentes() {
        return ResponseEntity.ok(dashboardService.buscarAlertasRecentes());
    }

    // =========================================================================
    // 3.1 ALERTA DE PRODUTOS PENDENTES DE REVISÃO (DO PDV)
    // =========================================================================
    @GetMapping("/alertas/pendentes-revisao")
    @Operation(summary = "Contagem de produtos vindos do PDV que precisam de revisão")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<Long> contarProdutosPendentesDeRevisao() {
        return ResponseEntity.ok(dashboardService.contarProdutosPendentesDeRevisao());
    }

    // 4. Modal de Risco (Drill-Down do Estoque)
    @GetMapping("/risco-lista")
    @Operation(summary = "Lista de Produtos em Risco")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<Map<String, Object>>> getListaRisco(@RequestParam String tipo) {
        return ResponseEntity.ok(dashboardService.obterListaRisco(tipo));
    }

    // 5. Dados Fiscais
    @GetMapping("/fiscal")
    @Operation(summary = "Dados Fiscais")
    public ResponseEntity<FiscalDashboardDTO> getDadosFiscais(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fim == null) fim = LocalDate.now();

        return ResponseEntity.ok(dashboardService.getResumoFiscal(inicio, fim));
    }

    // Retorna os alertas da IA para o Frontend
    @GetMapping("/insights")
    public ResponseEntity<?> getInsightsIA() {
        return ResponseEntity.ok(insightIARepo.findByResolvidoFalseOrderByCriticidadeAscDataGeracaoDesc());
    }

    // Permite disparar o cálculo manualmente para testar agora mesmo
    @PostMapping("/insights/forcar-analise")
    public ResponseEntity<String> forcarAnaliseIA() {
        motorIA.processarInsightsDiarios();
        return ResponseEntity.ok("Análise da IA executada com sucesso!");
    }

    // Permite ao gestor dispensar um alerta
    @PutMapping("/insights/{id}/resolver")
    public ResponseEntity<?> resolverInsight(@PathVariable Long id) {
        return insightIARepo.findById(id).map(insight -> {
            insight.setResolvido(true);
            insightIARepo.save(insight);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
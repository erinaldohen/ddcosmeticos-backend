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
@Tag(name = "Dashboard", description = "Endpoints para KPIs, Indicadores Fiscais e Painel de Inteligência")
public class DashboardController {

    private final MotorInteligenciaService motorIA;
    private final InsightIARepository insightIARepo;
    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Carregar Dashboard Dinâmico (KPIs)")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<Map<String, Object>> getDashboardData(@RequestParam(defaultValue = "este_mes") String periodo) {
        return ResponseEntity.ok(dashboardService.obterDadosDoDashboard(periodo));
    }

    @GetMapping("/resumo")
    @Operation(summary = "Resumo Operacional (Vendas vs Metas)")
    public ResponseEntity<DashboardResumoDTO> getResumoGeral() {
        return ResponseEntity.ok(dashboardService.obterResumoGeral());
    }

    @GetMapping("/alertas")
    @Operation(summary = "Alertas Recentes da Trilha de Auditoria")
    public ResponseEntity<List<AuditoriaRequestDTO>> getAlertasRecentes() {
        return ResponseEntity.ok(dashboardService.buscarAlertasRecentes());
    }

    @GetMapping("/alertas/pendentes-revisao")
    @Operation(summary = "Contagem de produtos vindos do PDV que precisam de revisão do Gestor")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<Long> contarProdutosPendentesDeRevisao() {
        return ResponseEntity.ok(dashboardService.contarProdutosPendentesDeRevisao());
    }

    @GetMapping("/risco-lista")
    @Operation(summary = "Drill-Down de Produtos em Risco (Estoque/Validade)")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<Map<String, Object>>> getListaRisco(@RequestParam String tipo) {
        return ResponseEntity.ok(dashboardService.obterListaRisco(tipo));
    }

    @GetMapping("/fiscal")
    @Operation(summary = "Gráficos de Projeção Fiscal (ICMS/Simples)")
    public ResponseEntity<FiscalDashboardDTO> getDadosFiscais(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fim == null) fim = LocalDate.now();

        return ResponseEntity.ok(dashboardService.getResumoFiscal(inicio, fim));
    }

    @GetMapping("/insights")
    @Operation(summary = "Ler insights e alertas gerados pela IA Diária")
    public ResponseEntity<?> getInsightsIA() {
        return ResponseEntity.ok(insightIARepo.findByResolvidoFalseOrderByCriticidadeAscDataGeracaoDesc());
    }

    @PostMapping("/insights/forcar-analise")
    @Operation(summary = "Forçar varredura da IA sobre a base de dados (Manual)")
    public ResponseEntity<String> forcarAnaliseIA() {
        motorIA.processarInsightsDiarios();
        return ResponseEntity.ok("Análise da IA executada com sucesso!");
    }

    @PutMapping("/insights/{id}/resolver")
    @Operation(summary = "Marcar alerta da IA como Resolvido/Ignorado")
    public ResponseEntity<?> resolverInsight(@PathVariable Long id) {
        return insightIARepo.findById(id).map(insight -> {
            insight.setResolvido(true);
            insightIARepo.save(insight);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
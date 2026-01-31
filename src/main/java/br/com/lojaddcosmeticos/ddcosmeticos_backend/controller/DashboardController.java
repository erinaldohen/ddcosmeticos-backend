package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardDTO; // <--- Importante: Novo DTO
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    // 1. Endpoint Principal para a Tela de Vendas (Gráficos e Cards Financeiros)
    // O Frontend chama este endpoint para preencher a tela inicial
    @GetMapping
    public ResponseEntity<DashboardDTO> getDashboardCompleto() {
        return ResponseEntity.ok(dashboardService.carregarDashboard());
    }

    // 2. Resumo Operacional (Focado em Estoque e Alertas - Pode ser usado em outra aba)
    @GetMapping("/resumo")
    public ResponseEntity<DashboardResumoDTO> getResumo() {
        return ResponseEntity.ok(dashboardService.obterResumoGeral());
    }

    // 3. Alertas de Auditoria (Polling)
    @GetMapping("/alertas")
    public ResponseEntity<List<AuditoriaRequestDTO>> getAlertas() {
        return ResponseEntity.ok(dashboardService.buscarAlertasRecentes());
    }
}
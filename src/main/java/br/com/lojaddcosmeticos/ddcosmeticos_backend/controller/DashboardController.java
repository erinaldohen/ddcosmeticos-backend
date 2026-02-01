package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardDTO;
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

    // Endpoint Principal
    @GetMapping
    public ResponseEntity<DashboardDTO> getDashboardCompleto() {
        return ResponseEntity.ok(dashboardService.carregarDashboard());
    }

    @GetMapping("/resumo")
    public ResponseEntity<DashboardResumoDTO> getResumo() {
        return ResponseEntity.ok(dashboardService.obterResumoGeral());
    }

    @GetMapping("/alertas")
    public ResponseEntity<List<AuditoriaRequestDTO>> getAlertas() {
        return ResponseEntity.ok(dashboardService.buscarAlertasRecentes());
    }
}
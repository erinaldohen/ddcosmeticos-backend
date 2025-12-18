package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador responsável por expor os indicadores de performance (KPIs) da DD Cosméticos.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Indicadores financeiros e operacionais em tempo real")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * Retorna o resumo consolidado para o painel principal.
     * Restrito ao perfil GERENTE, pois contém dados sensíveis de faturamento e margem.
     */
    @GetMapping("/resumo")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(
            summary = "Obter Resumo Geral",
            description = "Retorna faturamento do dia, total de vendas, projeção financeira para 7 dias e alertas de stock."
    )
    public ResponseEntity<DashboardResponseDTO> obterResumo() {
        // O serviço já orquestra as consultas otimizadas nos 4 repositórios (Venda, Produto, Pagar e Receber)
        return ResponseEntity.ok(dashboardService.obterResumo());
    }
}
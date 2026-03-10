package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CRMService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/crm")
@RequiredArgsConstructor
@Tag(name = "CRM", description = "Gestão de Relacionamento e Inteligência de Clientes")
public class CRMController {

    private final CRMService crmService;

    @GetMapping("/dashboard")
    @Operation(summary = "Obter Dados do CRM (Tarefas, Clientes e KPIs)")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'OPERADOR')")
    public ResponseEntity<Map<String, Object>> getCrmDashboard() {
        return ResponseEntity.ok(crmService.obterDadosCRM());
    }
}
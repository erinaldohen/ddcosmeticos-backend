package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RegistrarInteracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CRMService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/crm")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Permite chamadas do React
public class CRMController {

    private final CRMService crmService;

    // Rota que alimenta a tela principal do CRM
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<Map<String, Object>> getCrmDashboard() {
        return ResponseEntity.ok(crmService.obterDadosCRM());
    }

    // NOVA ROTA: Salva a interação quando o vendedor clica no WhatsApp
    @PostMapping("/interacao")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<String> registrarInteracao(@Valid @RequestBody RegistrarInteracaoDTO dto) {
        crmService.registrarInteracao(dto);
        return ResponseEntity.ok("Interação registrada com sucesso!");
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RegistrarInteracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CRMService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/crm")
@RequiredArgsConstructor
@Tag(name = "CRM", description = "Gestão de Relacionamento com Clientes (WhatsApp e Dashboard)")
public class CRMController {

    private final CRMService crmService;

    @GetMapping("/dashboard")
    @Operation(summary = "Métricas do Dashboard CRM", description = "Fornece KPIs de engajamento e métricas matemáticas para o gráfico.")
    public ResponseEntity<?> getCrmDashboard(HttpServletRequest request) {
        try {
            log.info("🟢 Requisição recebida com sucesso no CRM Dashboard!");
            Map<String, Object> dados = crmService.obterDadosCRM();
            log.info("🟢 Dados processados, enviando para o React.");
            return ResponseEntity.ok(dados);
        } catch (Exception e) {
            log.error("🚨 ERRO INTERNO AO CARREGAR MOTOR DO CRM: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Falha ao calcular métricas do CRM: " + e.getMessage()));
        }
    }

    @PostMapping("/interacao")
    @Operation(summary = "Registrar Interação", description = "Guarda o log de quando a loja comunicou (Ex: via WhatsApp) com um cliente.")
    public ResponseEntity<?> registrarInteracao(@Valid @RequestBody RegistrarInteracaoDTO dto) {
        try {
            crmService.registrarInteracao(dto);
            return ResponseEntity.ok(Map.of("mensagem", "Interação registada com sucesso!"));
        } catch (Exception e) {
            log.error("🚨 ERRO AO SALVAR INTERAÇÃO DO WHATSAPP: ", e);
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}
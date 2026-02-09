package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioMensalService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/relatorios")
@Tag(name = "Relatórios", description = "Análises gerenciais da DD Cosméticos")
@CrossOrigin("*")
public class RelatorioController {

    @Autowired
    private RelatorioService relatorioService;
    @Autowired
    private RelatorioMensalService relatorioMensalService;

    @PostMapping("/enviar-mensal")
    @Operation(summary = "Forçar envio do relatório mensal por e-mail")
    @PreAuthorize("hasRole('ADMIN')") // Apenas admin pode disparar
    public ResponseEntity<String> enviarRelatorioMensal(@RequestParam String email) {
        // Chama o serviço assíncrono (não trava a requisição)
        relatorioMensalService.processarRelatorioMensal(email);
        return ResponseEntity.ok("Solicitação de relatório recebida. Verifique o e-mail em instantes.");
    }

    @GetMapping("/vendas")
    @Operation(summary = "Relatório Geral de Vendas", description = "Retorna faturamento, ticket médio e ranking de produtos.")
    public ResponseEntity<RelatorioVendasDTO> obterRelatorioVendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        return ResponseEntity.ok(relatorioService.gerarRelatorioVendas(inicio, fim));
    }
}
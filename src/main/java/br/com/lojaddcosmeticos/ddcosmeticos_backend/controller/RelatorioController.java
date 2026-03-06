package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioMensalService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/v1/relatorios")
@Tag(name = "Relatórios", description = "Análises gerenciais da DD Cosméticos")
@CrossOrigin("*")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final RelatorioMensalService relatorioMensalService;

    // =========================================================================
    // 1. DISPARO DE E-MAIL MENSAL
    // =========================================================================

    @PostMapping("/enviar-mensal")
    @Operation(summary = "Forçar envio do relatório mensal por e-mail")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> enviarRelatorioMensal(@RequestParam(required = false) String email) {
        String emailDestino = email;

        if (emailDestino == null || emailDestino.isBlank()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                emailDestino = auth.getName();
            } else {
                return ResponseEntity.badRequest().body("Erro: E-mail não informado e usuário não identificado.");
            }
        }

        relatorioMensalService.processarRelatorioMensal(emailDestino);
        return ResponseEntity.ok("Relatório será enviado para " + emailDestino + " em instantes.");
    }

    // =========================================================================
    // 2. ROTA DINÂMICA PARA O BI (REACT) - BLINDAGEM TOTAL COM MAP
    // =========================================================================

    @GetMapping("/{categoria}")
    @Operation(summary = "Relatórios Dinâmicos de BI", description = "Retorna dados analíticos baseados na aba selecionada.")
    public ResponseEntity<?> obterRelatorioDinamico(
            @PathVariable String categoria,
            // A Opção Nuclear: Recebe qualquer parâmetro da URL como um mapa de textos
            @RequestParam Map<String, String> parametros) {

        log.info("Requisição recebida na categoria: '{}' com os parâmetros brutos: {}", categoria, parametros);

        LocalDate inicio = null;
        LocalDate fim = null;

        // Extraímos e convertemos as datas manualmente, blindando contra o Erro 400
        try {
            if (parametros.containsKey("inicio") && !parametros.get("inicio").trim().isEmpty()) {
                inicio = LocalDate.parse(parametros.get("inicio"));
            }
            if (parametros.containsKey("fim") && !parametros.get("fim").trim().isEmpty()) {
                fim = LocalDate.parse(parametros.get("fim"));
            }
        } catch (Exception e) {
            log.warn("Erro ao converter as datas recebidas do React. Usando período padrão. Detalhe: {}", e.getMessage());
        }

        if ("vendas".equalsIgnoreCase(categoria)) {
            return ResponseEntity.ok(relatorioService.gerarRelatorioVendas(inicio, fim));
        } else if ("estoque".equalsIgnoreCase(categoria)) {
            return ResponseEntity.ok(relatorioService.gerarRelatorioEstoque(inicio, fim));
        } else if ("financeiro".equalsIgnoreCase(categoria)) {
            return ResponseEntity.ok(relatorioService.gerarRelatorioFinanceiro(inicio, fim));
        } else if ("fiscal".equalsIgnoreCase(categoria)) {
            return ResponseEntity.ok(relatorioService.gerarRelatorioFiscal(inicio, fim));
        }

        return ResponseEntity.notFound().build();
    }
}
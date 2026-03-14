package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SimulacaoTributariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ValidacaoFiscalDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CalculadoraFiscalService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.DashboardService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FiscalComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/fiscal")
@RequiredArgsConstructor
public class FiscalController {

    @Autowired
    private FiscalComplianceService fiscalService;

    @Autowired
    private CalculadoraFiscalService calculadoraFiscalService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/simular-reforma/{codigoBarras}")
    public ResponseEntity<SimulacaoTributariaDTO> simularImpacto(@PathVariable String codigoBarras) {
        return produtoRepository.findByCodigoBarras(codigoBarras)
                .map(produto -> {
                    try {
                        fiscalService.auditarDadosFiscais(produto);
                    } catch (IllegalArgumentException e) {
                        // Log ou header de aviso opcional
                    }
                    SimulacaoTributariaDTO simulacao = fiscalService.simularImpactoReforma(produto);
                    return ResponseEntity.ok(simulacao);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/auditar-produto")
    public ResponseEntity<String> auditarProduto(@RequestBody Produto produto) {
        try {
            fiscalService.auditarDadosFiscais(produto);
            return ResponseEntity.ok("Produto em conformidade fiscal (NCM e Estrutura válidos).");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/validar")
    public ResponseEntity<ValidacaoFiscalDTO.Response> validarDados(@RequestBody ValidacaoFiscalDTO.Request dados) {
        var resultado = calculadoraFiscalService.simularValidacao(dados.descricao(), dados.ncm());
        return ResponseEntity.ok(resultado);
    }

    // =========================================================================
    // APENAS UM MÉTODO PARA O DASHBOARD (Opção Blindada ativada)
    // =========================================================================
    @GetMapping("/dashboard-resumo")
    public ResponseEntity<?> getResumoFiscal(HttpServletRequest request) {
        try {
            // Extração manual e blindada para evitar bloqueios do Spring
            String inicioRaw = request.getParameter("inicio");
            String fimRaw = request.getParameter("fim");

            if (inicioRaw == null || fimRaw == null) {
                return ResponseEntity.badRequest().body("As datas de início e fim são obrigatórias.");
            }

            LocalDate inicio = LocalDate.parse(inicioRaw);
            LocalDate fim = LocalDate.parse(fimRaw);

            log.info("Buscando Painel Fiscal de {} até {}", inicio, fim);

            // Chama o Service existente
            FiscalDashboardDTO resumo = dashboardService.getResumoFiscal(inicio, fim);

            return ResponseEntity.ok(resumo);

        } catch (Exception e) {
            log.error("🚨 ERRO 500 - PAINEL FISCAL: ", e);
            return ResponseEntity.internalServerError().body("Falha ao processar dados fiscais: " + e.getMessage());
        }
    }
}
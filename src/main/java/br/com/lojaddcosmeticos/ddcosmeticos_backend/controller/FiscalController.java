package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SimulacaoTributariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ValidacaoFiscalDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CalculadoraFiscalService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.DashboardService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FiscalComplianceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/fiscal")
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
        // Agora busca por 'findByCodigoBarras' em vez de 'findById'
        return produtoRepository.findByCodigoBarras(codigoBarras)
                .map(produto -> {
                    // Opcional: Validar dados antes de simular
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

    @GetMapping("/dashboard-resumo")
    public ResponseEntity<FiscalDashboardDTO> getDashboardResumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fim == null) fim = LocalDate.now();

        return ResponseEntity.ok(dashboardService.getResumoFiscal(inicio, fim));
    }
}
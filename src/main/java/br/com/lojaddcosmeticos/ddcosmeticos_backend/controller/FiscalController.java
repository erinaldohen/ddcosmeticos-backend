package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SimulacaoTributariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FiscalComplianceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fiscal")
public class FiscalController {

    @Autowired
    private FiscalComplianceService fiscalService;

    @Autowired
    private ProdutoRepository produtoRepository;

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
}
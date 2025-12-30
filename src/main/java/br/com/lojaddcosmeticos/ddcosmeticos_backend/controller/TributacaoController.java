package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SplitPaymentDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CalculadoraFiscalService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tributacao")
@Tag(name = "Tributação e Fiscal", description = "Simulações da Reforma Tributária 2026 e Split Payment")
public class TributacaoController {

    @Autowired
    private CalculadoraFiscalService calculadoraFiscalService;

    @Autowired
    private ProdutoService produtoService;

    // --- Endpoint Simples (Mantido) ---
    @GetMapping("/simular-iva-2026")
    public ResponseEntity<Map<String, BigDecimal>> simularIva2026(
            @RequestParam BigDecimal valorVenda,
            @RequestParam(defaultValue = "false") boolean isMonofasico) {
        return ResponseEntity.ok(calculadoraFiscalService.simularTributacao2026(valorVenda, isMonofasico));
    }

    // --- Endpoint Gerencial (Mantido) ---
    @GetMapping("/comparativo-regimes")
    public ResponseEntity<String> compararRegimes(
            @RequestParam BigDecimal faturamentoMensal,
            @RequestParam BigDecimal comprasMensais) {
        return ResponseEntity.ok(calculadoraFiscalService.analisarCenarioMaisVantajoso(faturamentoMensal, comprasMensais));
    }

    // --- NOVO: Endpoint para o PDV (Split Payment) ---
    @PostMapping("/calcular-split-venda")
    @Operation(summary = "Calcular Split Payment (Pré-Venda)", description = "Recebe uma lista de IDs de produtos e quantidades para prever a retenção do banco.")
    public ResponseEntity<SplitPaymentDTO> calcularSplit(@RequestBody List<ItemSplitRequest> itensRequest) {
        // Converte o Request simples em Itens de Venda para a calculadora processar
        List<ItemVenda> itens = new ArrayList<>();

        for (ItemSplitRequest req : itensRequest) {
            Produto p = produtoService.buscarPorId(req.idProduto());
            ItemVenda item = new ItemVenda();
            item.setProduto(p);
            item.setQuantidade(req.quantidade());
            item.setPrecoUnitario(p.getPrecoVenda()); // Usa preço atual
            itens.add(item);
        }

        return ResponseEntity.ok(calculadoraFiscalService.calcularSplitPayment(itens));
    }

    // Record auxiliar para o Request
    public record ItemSplitRequest(Long idProduto, BigDecimal quantidade) {}
}
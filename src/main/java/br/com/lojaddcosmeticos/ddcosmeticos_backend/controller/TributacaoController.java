package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoFiscalCarrinhoDTO;
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
@Tag(name = "Tributação e Fiscal", description = "Simulações da Reforma Tributária 2026 e Split Payment Automático")
public class TributacaoController {

    @Autowired
    private CalculadoraFiscalService calculadoraFiscalService;

    @Autowired
    private ProdutoService produtoService;

    @PostMapping("/simular-carrinho")
    @Operation(summary = "Simula os impostos incidentes sobre um conjunto de itens do PDV")
    public ResponseEntity<ResumoFiscalCarrinhoDTO> simularCarrinho(@RequestBody List<ItemSplitRequest> itensDto) {

        List<ItemVenda> itensParaCalculo = new ArrayList<>();
        for (ItemSplitRequest req : itensDto) {
            Produto p = produtoService.buscarPorId(req.idProduto());
            ItemVenda item = new ItemVenda();
            item.setProduto(p);
            item.setQuantidade(req.quantidade());
            item.setPrecoUnitario(p.getPrecoVenda());
            itensParaCalculo.add(item);
        }

        ResumoFiscalCarrinhoDTO resumo = calculadoraFiscalService.calcularTotaisCarrinho(itensParaCalculo);
        return ResponseEntity.ok(resumo);
    }

    @GetMapping("/simular-iva-2026")
    @Operation(summary = "Comparador Simples Nacional Atual vs IVA Dual/IBS (2026)")
    public ResponseEntity<Map<String, BigDecimal>> simularIva2026(
            @RequestParam BigDecimal valorVenda,
            @RequestParam(defaultValue = "false") boolean isMonofasico) {
        return ResponseEntity.ok(calculadoraFiscalService.simularTributacao2026(valorVenda, isMonofasico));
    }

    @GetMapping("/comparativo-regimes")
    @Operation(summary = "Gera um texto avaliativo recomendando o melhor regime tributário")
    public ResponseEntity<String> compararRegimes(
            @RequestParam BigDecimal faturamentoMensal,
            @RequestParam BigDecimal comprasMensais) {
        return ResponseEntity.ok(calculadoraFiscalService.analisarCenarioMaisVantajoso(faturamentoMensal, comprasMensais));
    }

    @PostMapping("/calcular-split-venda")
    @Operation(summary = "Calcular Split Payment (Pré-Venda)", description = "Recebe uma lista de IDs de produtos para prever a retenção tributária do banco/adquirente.")
    public ResponseEntity<SplitPaymentDTO> calcularSplit(@RequestBody List<ItemSplitRequest> itensRequest) {
        List<ItemVenda> itens = new ArrayList<>();
        for (ItemSplitRequest req : itensRequest) {
            Produto p = produtoService.buscarPorId(req.idProduto());
            ItemVenda item = new ItemVenda();
            item.setProduto(p);
            item.setQuantidade(req.quantidade());
            item.setPrecoUnitario(p.getPrecoVenda());
            itens.add(item);
        }
        return ResponseEntity.ok(calculadoraFiscalService.calcularSplitPayment(itens));
    }

    public record ItemSplitRequest(Long idProduto, BigDecimal quantidade) {}
}
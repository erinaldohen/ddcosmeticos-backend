package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoInventarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.InventarioInteligenteService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventario")
@Tag(name = "Contagem e Inventário (Balanço)", description = "Módulo de balanço geral e rastreio físico de perdas/vencidos")
public class InventarioController {

    @Autowired private ProdutoService produtoService;
    @Autowired private InventarioInteligenteService inventarioInteligenteService;
    @Autowired private RelatorioService relatorioService;

    @GetMapping
    @Operation(summary = "Listagem Plana para Balanço", description = "Traz o estoque atual com capacidade de filtrar vencidos e baixas.")
    public ResponseEntity<List<Produto>> listarInventario(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false, defaultValue = "todos") String status) {

        List<Produto> produtos = produtoService.listarTodosAtivos();

        if (busca != null && !busca.isEmpty()) {
            String termo = busca.toLowerCase();
            produtos = produtos.stream()
                    .filter(p -> p.getDescricao().toLowerCase().contains(termo) ||
                            (p.getSku() != null && p.getSku().toLowerCase().contains(termo)))
                    .collect(Collectors.toList());
        }

        if ("vencidos".equalsIgnoreCase(status)) {
            LocalDate hoje = LocalDate.now();
            produtos = produtos.stream()
                    .filter(p -> p.getValidade() != null && p.getValidade().getYear() > 1970 && p.getValidade().isBefore(hoje))
                    .collect(Collectors.toList());
        } else if ("baixo_estoque".equalsIgnoreCase(status)) {
            produtos = produtos.stream()
                    .filter(p -> p.getQuantidadeEmEstoque() != null &&
                            p.getQuantidadeEmEstoque() <= (p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(produtos);
    }

    @GetMapping(value = "/exportar", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Folha de Balanço Físico (PDF)", description = "Gera grelha PDF para os funcionários anotarem o inventário na prateleira.")
    public ResponseEntity<byte[]> exportarRelatorio() {
        try {
            List<SugestaoCompraDTO> sugestoes = produtoService.gerarSugestaoCompra();
            byte[] pdfBytes = relatorioService.gerarPdfSugestaoCompras(sugestoes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Balanco_Inventario_" + System.currentTimeMillis() + ".pdf");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            log.error("Erro ao gerar folha de balanço", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/inteligente")
    @Operation(summary = "Auditoria Cega", description = "Compara a contagem inserida pelo funcionário contra o sistema para achar perdas de retalho.")
    public ResponseEntity<List<ProdutoInventarioDTO>> obterInventarioInteligente() {
        return ResponseEntity.ok(inventarioInteligenteService.gerarRelatorioInteligente());
    }
}
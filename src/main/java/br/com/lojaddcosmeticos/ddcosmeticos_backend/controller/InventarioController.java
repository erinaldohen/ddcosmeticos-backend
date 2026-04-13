package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoInventarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.InventarioInteligenteService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/inventario")
public class InventarioController {

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private InventarioInteligenteService inventarioInteligenteService;

    @Autowired
    private RelatorioService relatorioService;

    // 1. LISTAR ESTOQUE (COM FILTROS SIMPLES)
    @GetMapping
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
                    // 🔥 BLINDAGEM: Ignora nulos e datas falsas (1970)
                    .filter(p -> p.getValidade() != null &&
                            p.getValidade().getYear() > 1970 &&
                            p.getValidade().isBefore(hoje))
                    .collect(Collectors.toList());
        } else if ("baixo_estoque".equalsIgnoreCase(status)) {
            produtos = produtos.stream()
                    // 🔥 BLINDAGEM: Previne NullPointerException se a quantidade ou mínimo vierem nulos do banco
                    .filter(p -> p.getQuantidadeEmEstoque() != null &&
                            p.getQuantidadeEmEstoque() <= (p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(produtos);
    }

    // 2. EXPORTAR RELATÓRIO PDF
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarRelatorio() {
        try {
            List<SugestaoCompraDTO> sugestoes = produtoService.gerarSugestaoCompra();
            byte[] pdfBytes = relatorioService.gerarPdfSugestaoCompras(sugestoes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String filename = "Inventario_" + System.currentTimeMillis() + ".pdf";
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // 3. O NOVO CÉREBRO DE OPERAÇÕES
    @GetMapping("/inteligente")
    public ResponseEntity<List<ProdutoInventarioDTO>> obterInventarioInteligente() {
        return ResponseEntity.ok(inventarioInteligenteService.gerarRelatorioInteligente());
    }
}
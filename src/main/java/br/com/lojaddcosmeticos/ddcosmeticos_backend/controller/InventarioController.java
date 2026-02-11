package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/inventario") // <--- ROTA CORRETA
public class InventarioController {

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private RelatorioService relatorioService;

    // 1. LISTAR ESTOQUE (COM FILTROS SIMPLES)
    // Frontend chama: /api/v1/inventario?busca=&status=todos
    @GetMapping
    public ResponseEntity<List<Produto>> listarInventario(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false, defaultValue = "todos") String status) {

        // Busca todos os produtos ativos
        List<Produto> produtos = produtoService.listarTodosAtivos();

        // Filtra na memória (simples e eficaz para volumes moderados)
        // Se o volume for gigante, ideal mover para @Query no Repository
        if (busca != null && !busca.isEmpty()) {
            String termo = busca.toLowerCase();
            produtos = produtos.stream()
                    .filter(p -> p.getDescricao().toLowerCase().contains(termo) ||
                            (p.getSku() != null && p.getSku().toLowerCase().contains(termo)))
                    .collect(Collectors.toList());
        }

        if ("vencidos".equalsIgnoreCase(status)) {
            produtos = produtos.stream().filter(Produto::isVencido).collect(Collectors.toList());
        } else if ("baixo_estoque".equalsIgnoreCase(status)) {
            produtos = produtos.stream().filter(p -> p.getQuantidadeEmEstoque() <= p.getEstoqueMinimo()).collect(Collectors.toList());
        }

        return ResponseEntity.ok(produtos);
    }

    // 2. EXPORTAR RELATÓRIO PDF
    // Frontend chama: /api/v1/inventario/exportar
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarRelatorio() {
        try {
            // Reaproveita a lógica de sugestão de compra ou cria um novo método específico
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
}
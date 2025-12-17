package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/produtos")
@Tag(name = "Produtos", description = "Gestão de inventário e importação de stock")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    /**
     * Endpoint para importação massiva via ficheiro CSV/Excel.
     * Apenas utilizadores com perfil GERENTE podem realizar esta operação.
     */
    @PostMapping("/importar")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Importar Stock CSV", description = "Processa o ficheiro de stock, mapeando campos fiscais e atualizando preços.")
    public ResponseEntity<String> importarEstoque(@RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            return ResponseEntity.badRequest().body("Por favor, selecione um ficheiro válido.");
        }

        produtoService.importarEstoqueCSV(arquivo);
        return ResponseEntity.ok("Importação concluída com sucesso! Verifique os logs para detalhes.");
    }

    /**
     * Consulta rápida de produto para o PDV.
     */
    @GetMapping("/ean/{ean}")
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    @Operation(summary = "Buscar por EAN", description = "Retorna os dados do produto para venda imediata.")
    public ResponseEntity<Produto> buscarPorEan(@PathVariable String ean) {
        return ResponseEntity.ok(produtoService.buscarPorCodigoBarras(ean));
    }
}
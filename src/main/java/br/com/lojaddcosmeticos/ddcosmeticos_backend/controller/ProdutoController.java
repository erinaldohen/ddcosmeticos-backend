package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    // Endpoint para consulta no PDV por Código de Barras
    @GetMapping
    public ResponseEntity<ProdutoDTO> buscarProdutoPorEan(@RequestParam("ean") String ean) {

        Produto produto = produtoService.buscarPorCodigoBarras(ean);

        if (produto == null) {
            // Retorna 404 se o produto não for encontrado
            return ResponseEntity.notFound().build();
        }

        // Mapeia a entidade para o DTO de resposta
        ProdutoDTO dto = new ProdutoDTO(produto);

        return ResponseEntity.ok(dto);
    }
}

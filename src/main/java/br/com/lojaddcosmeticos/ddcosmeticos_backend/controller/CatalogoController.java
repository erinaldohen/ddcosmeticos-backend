package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoVisualDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CatalogoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    @Autowired
    private CatalogoService catalogoService;

    // Exemplo de uso: /api/catalogo/busca?termo=shampoo
    // Ou vazio: /api/catalogo/busca (traz os destaques)
    @GetMapping("/busca")
    public ResponseEntity<List<ProdutoVisualDTO>> buscar(@RequestParam(required = false) String termo) {
        List<ProdutoVisualDTO> resultado = catalogoService.buscarProdutos(termo);

        if (resultado.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(resultado);
    }
}
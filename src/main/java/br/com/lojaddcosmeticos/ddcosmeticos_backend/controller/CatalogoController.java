package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoVisualDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogo") // 🔥 ADICIONADO O V1 PARA MANTER CONSISTÊNCIA DA API
@Tag(name = "Catálogo Web (Montra)", description = "Busca Ultraleve otimizada para o Site/Frontend (Cliente Final)")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogoService;

    @GetMapping("/busca")
    @Operation(summary = "Motor de Busca de Artigos Puros", description = "Se o termo for vazio, retorna os Destaques da Loja.")
    public ResponseEntity<List<ProdutoVisualDTO>> buscar(@RequestParam(required = false) String termo) {
        List<ProdutoVisualDTO> resultado = catalogoService.buscarProdutos(termo);
        return resultado.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(resultado);
    }
}
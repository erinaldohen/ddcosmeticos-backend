package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.config.DataSeeder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administração", description = "Rotas administrativas")
public class AdminController {

    @Autowired
    private DataSeeder dataSeeder;

    @PostMapping("/importar-csv")
    @Operation(summary = "Importar Produtos CSV", description = "Processa o arquivo 'produtos.csv' na pasta resources.")
    public ResponseEntity<String> importarProdutos() {
        // Chama o método do seu DataSeeder atualizado
        String resultado = dataSeeder.importarProdutos();
        return ResponseEntity.ok(resultado);
    }
}
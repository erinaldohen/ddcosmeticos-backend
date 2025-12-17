package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FornecedorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fornecedores")
public class FornecedorController {

    @Autowired
    private FornecedorService fornecedorService;

    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Fornecedor> cadastrarFornecedor(@RequestBody Fornecedor fornecedor) {
        try {
            Fornecedor saved = fornecedorService.salvar(fornecedor);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    public ResponseEntity<Fornecedor> buscarFornecedor(@RequestParam String cnpjCpf) {
        // O Service lança exceção 404 se não achar, o Spring trata globalmente se configurado,
        // ou podemos usar try-catch aqui para ser explícito.
        try {
            return ResponseEntity.ok(fornecedorService.buscarPorCnpjCpf(cnpjCpf));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
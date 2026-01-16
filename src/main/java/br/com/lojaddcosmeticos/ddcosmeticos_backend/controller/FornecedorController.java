package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FornecedorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fornecedores")
public class FornecedorController {

    @Autowired
    private FornecedorService fornecedorService;

    // --- 1. CADASTRO (MANTIDO) ---
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

    // --- 2. BUSCA POR DOCUMENTO (MANTIDO - REFINADO) ---
    // Adicionei params = "cnpjCpf" para diferenciar da listagem geral
    @GetMapping(params = "cnpjCpf")
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE', 'ESTOQUISTA')") // Adicionei Estoquista pois ele precisa buscar
    public ResponseEntity<Fornecedor> buscarFornecedor(@RequestParam String cnpjCpf) {
        try {
            return ResponseEntity.ok(fornecedorService.buscarPorCnpjCpf(cnpjCpf));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --- 3. NOVO: LISTAR TODOS (PARA O DROPDOWN DA ENTRADA DE ESTOQUE) ---
    @GetMapping
    @PreAuthorize("isAuthenticated()") // Qualquer usuário logado pode ver a lista para selecionar
    public ResponseEntity<List<Fornecedor>> listarTodos() {
        return ResponseEntity.ok(fornecedorService.listarTodos());
    }

    // --- 4. NOVO: INTELIGÊNCIA DE COMPRAS (BI) ---
    @GetMapping("/{id}/sugestao-compra")
    @PreAuthorize("hasAnyRole('GERENTE', 'ESTOQUISTA')")
    public ResponseEntity<List<Produto>> obterSugestaoCompra(@PathVariable Long id) {
        // Retorna produtos com estoque baixo que costumam ser comprados deste fornecedor
        List<Produto> sugestoes = fornecedorService.obterSugestaoDeCompra(id);

        if (sugestoes.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(sugestoes);
    }
}
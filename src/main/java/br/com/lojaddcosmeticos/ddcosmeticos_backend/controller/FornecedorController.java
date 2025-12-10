// Local: src/main/java/br/com.lojaddcosmeticos.ddcosmeticos_backend.controller/FornecedorController.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/fornecedores")
public class FornecedorController {

    @Autowired
    private FornecedorRepository fornecedorRepository;

    /**
     * Permite ao Gerente cadastrar novos fornecedores.
     */
    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Fornecedor> cadastrarFornecedor(@RequestBody Fornecedor fornecedor) {
        // Verifica se já existe um CNPJ/CPF
        Optional<Fornecedor> existing = fornecedorRepository.findByCnpjCpf(fornecedor.getCnpjCpf());
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // 409 Conflict
        }

        // Nota: A validação detalhada (CPF/CNPJ válido) virá na próxima fase
        Fornecedor saved = fornecedorRepository.save(fornecedor);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Consulta de fornecedor por CNPJ/CPF
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')") // Caixa pode precisar consultar
    public ResponseEntity<Fornecedor> buscarFornecedor(@RequestParam String cnpjCpf) {
        return fornecedorRepository.findByCnpjCpf(cnpjCpf)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FornecedorDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO; // Import necessário para produtos
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FornecedorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable; // Adicionado
import org.springframework.data.domain.Sort;
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

    // --- 1. LEITURA ---

    @GetMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE', 'ESTOQUISTA', 'ADMIN')")
    public ResponseEntity<Page<FornecedorDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String termo) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("nomeFantasia"));
        return ResponseEntity.ok(fornecedorService.listar(termo, pageRequest));
    }

    @GetMapping("/dropdown")
    public ResponseEntity<List<FornecedorDTO>> listarParaDropdown() {
        return ResponseEntity.ok(fornecedorService.listarTodosParaDropdown());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ESTOQUISTA', 'ADMIN')")
    public ResponseEntity<FornecedorDTO> buscarPorId(@PathVariable Long id) {
        // [CORREÇÃO LINHA 52]
        // O serviço já retorna o DTO e trata a exceção se não encontrar.
        // Removemos a chamada estranha de atualizar() dentro do get().
        return ResponseEntity.ok(fornecedorService.buscarPorId(id));
    }

    // --- 2. ESCRITA ---

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    public ResponseEntity<FornecedorDTO> cadastrar(@RequestBody @Valid FornecedorDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fornecedorService.salvar(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    public ResponseEntity<FornecedorDTO> atualizar(@PathVariable Long id, @RequestBody @Valid FornecedorDTO dto) {
        return ResponseEntity.ok(fornecedorService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        fornecedorService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    // --- 3. EXTRAS ---

    // Ajustado para retornar produtos do fornecedor (usando o método que criamos no Service)
    @GetMapping("/{id}/produtos")
    public ResponseEntity<Page<ProdutoListagemDTO>> listarProdutosDoFornecedor(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(fornecedorService.listarProdutosDoFornecedor(id, pageable));
    }

    @GetMapping("/consulta-cnpj/{cnpj}")
    public ResponseEntity<ConsultaCnpjDTO> consultarCnpjExterno(@PathVariable String cnpj) {
        // [CORREÇÃO] Nome do método no service é 'consultarDadosCnpj'
        return ResponseEntity.ok(fornecedorService.consultarDadosCnpj(cnpj));
    }
}
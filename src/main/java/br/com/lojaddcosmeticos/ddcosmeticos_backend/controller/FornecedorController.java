package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FornecedorDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FornecedorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Autowired
    private FornecedorRepository fornecedorRepository;

    // --- 1. LEITURA ---

    @GetMapping("/buscar-por-cnpj/{cnpj}")
    public ResponseEntity<?> buscarPorCnpj(@PathVariable String cnpj) {
        String cnpjLimpo = cnpj.replaceAll("\\D", ""); // Remove máscara
        // Usa o repository diretamente para ser rápido e resolver o erro 500 anterior
        return fornecedorRepository.findByCnpj(cnpjLimpo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

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
        return ResponseEntity.ok(fornecedorService.buscarPorId(id));
    }

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
        return ResponseEntity.ok(fornecedorService.consultarDadosCnpj(cnpj));
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

    // --- 3. EXCLUSÃO (CORRIGIDA) ---

    // Removemos o método 'excluir' antigo e mantivemos apenas este.
    // Agora existe apenas UM @DeleteMapping para esta rota.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        // Chama o serviço que faz a "Exclusão Lógica" (setAtivo = false)
        fornecedorService.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
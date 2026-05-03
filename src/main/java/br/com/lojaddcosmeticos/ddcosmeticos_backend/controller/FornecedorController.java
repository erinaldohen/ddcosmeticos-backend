package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FornecedorDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FornecedorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Fornecedores", description = "Gestão de Cadastro e Sincronização Receita Federal")
public class FornecedorController {

    @Autowired private FornecedorService fornecedorService;
    @Autowired private FornecedorRepository fornecedorRepository;

    @GetMapping("/buscar-por-cnpj/{cnpj}")
    @Operation(summary = "Buscar fornecedor exato pelo CNPJ")
    public ResponseEntity<?> buscarPorCnpj(@PathVariable String cnpj) {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");
        return fornecedorRepository.findByCnpj(cnpjLimpo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE', 'ESTOQUISTA', 'ADMIN')")
    @Operation(summary = "Listagem paginada de fornecedores")
    public ResponseEntity<Page<FornecedorDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String termo) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("nomeFantasia"));
        return ResponseEntity.ok(fornecedorService.listar(termo, pageRequest));
    }

    @GetMapping("/dropdown")
    @Operation(summary = "Listagem simples para selects (Comboboxes)")
    public ResponseEntity<List<FornecedorDTO>> listarParaDropdown() {
        return ResponseEntity.ok(fornecedorService.listarTodosParaDropdown());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ESTOQUISTA', 'ADMIN')")
    @Operation(summary = "Ficha cadastral do Fornecedor")
    public ResponseEntity<FornecedorDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(fornecedorService.buscarPorId(id));
    }

    @GetMapping("/{id}/produtos")
    @Operation(summary = "Quais produtos compramos deste Fornecedor?")
    public ResponseEntity<Page<ProdutoListagemDTO>> listarProdutosDoFornecedor(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(fornecedorService.listarProdutosDoFornecedor(id, pageable));
    }

    @GetMapping("/consulta-cnpj/{cnpj}")
    @Operation(summary = "API Externa da Receita Federal", description = "Autopreenchimento de dados cadastrais a partir do CNPJ")
    public ResponseEntity<ConsultaCnpjDTO> consultarCnpjExterno(@PathVariable String cnpj) {
        return ResponseEntity.ok(fornecedorService.consultarDadosCnpj(cnpj));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Adicionar Fornecedor")
    public ResponseEntity<FornecedorDTO> cadastrar(@RequestBody @Valid FornecedorDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fornecedorService.salvar(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Editar Fornecedor")
    public ResponseEntity<FornecedorDTO> atualizar(@PathVariable Long id, @RequestBody @Valid FornecedorDTO dto) {
        return ResponseEntity.ok(fornecedorService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Inativar Fornecedor (Soft Delete)")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        fornecedorService.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
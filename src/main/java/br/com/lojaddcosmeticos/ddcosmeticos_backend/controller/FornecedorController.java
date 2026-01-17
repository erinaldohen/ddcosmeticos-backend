package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FornecedorDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FornecedorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    // --- 1. LEITURA (GRID E DROPDOWN) ---

    // Grid Principal (Com paginação)
    @GetMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE', 'ESTOQUISTA')")
    public ResponseEntity<Page<FornecedorDTO>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(fornecedorService.listar(termo, PageRequest.of(page, size, Sort.by("nomeFantasia"))));
    }

    // Dropdown para Entrada de Estoque (Lista simples, todos os ativos)
    @GetMapping("/todos")
    public ResponseEntity<List<FornecedorDTO>> listarTodosParaSelect() {
        return ResponseEntity.ok(fornecedorService.listarTodosAtivos());
    }

    // Busca Específica por Documento (Usado na validação de cadastro)
    @GetMapping(params = "cnpjCpf")
    public ResponseEntity<FornecedorDTO> buscarPorDocumento(@RequestParam String cnpjCpf) {
        return fornecedorService.buscarPorCnpj(cnpjCpf)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- 2. ESCRITA (CADASTRO E EDIÇÃO) ---

    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<FornecedorDTO> cadastrar(@RequestBody @Valid FornecedorDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fornecedorService.salvar(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<FornecedorDTO> atualizar(@PathVariable Long id, @RequestBody @Valid FornecedorDTO dto) {
        return ResponseEntity.ok(fornecedorService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        fornecedorService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    // --- 3. RECURSOS INTELIGENTES (BI & INTEGRAÇÃO) ---

    @GetMapping("/{id}/sugestao-compra")
    @PreAuthorize("hasAnyRole('GERENTE', 'ESTOQUISTA')")
    public ResponseEntity<List<Produto>> obterSugestaoCompra(@PathVariable Long id) {
        List<Produto> sugestoes = fornecedorService.obterSugestaoDeCompra(id);
        if (sugestoes.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(sugestoes);
    }

    @GetMapping("/consulta-cnpj/{cnpj}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsultaCnpjDTO> consultarCnpjExterno(@PathVariable String cnpj) {
        // Busca dados na Receita Federal (via BrasilAPI) para preencher a tela automaticamente
        return ResponseEntity.ok(fornecedorService.consultarDadosPublicosCnpj(cnpj));
    }
}
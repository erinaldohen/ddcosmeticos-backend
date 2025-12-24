package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoPrecoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
// AJUSTADO PARA 'service' (SINGULAR)
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PrecificacaoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.services.integracao.CosmosService; // Esse geralmente mantemos em services/integracao, verifique sua pasta
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService service;

    @Autowired
    private ProdutoRepository repository;

    @Autowired
    private CosmosService cosmosService;

    @Autowired
    private PrecificacaoService precificacaoService;

    // --- 1. LISTAGEM E BUSCA UNIFICADA ---
    @GetMapping
    public ResponseEntity<List<Produto>> listar(@RequestParam(required = false) String termo) {
        return ResponseEntity.ok(service.buscarInteligente(termo));
    }

    // --- 2. CONSULTA EXTERNA ---
    @GetMapping("/consulta-externa/{ean}")
    public ResponseEntity<?> consultarExterno(@PathVariable String ean) {
        return cosmosService.consultarEan(ean)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- 3. ALERTA DE REPOSIÇÃO ---
    @GetMapping("/alerta-reposicao")
    public ResponseEntity<List<Produto>> listarBaixoEstoque() {
        var todos = repository.findAll();
        var criticos = todos.stream()
                .filter(p -> p.getQuantidadeEmEstoque() <= (p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0))
                .toList();
        return ResponseEntity.ok(criticos);
    }

    // --- 4. CADASTRO DE PRODUTO ---
    @PostMapping
    public ResponseEntity<?> cadastrar(@RequestBody @Valid ProdutoDTO dados) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.salvar(dados));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- 5. ATUALIZAR DADOS ---
    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoDTO dados) {
        return ResponseEntity.ok(service.atualizar(id, dados));
    }

    // --- 6. ENTRADA DE ESTOQUE ---
    @PostMapping("/estoque")
    public ResponseEntity<?> adicionarEstoque(
            @RequestParam String ean,
            @RequestParam Integer qtd,
            @RequestParam BigDecimal custo,
            @RequestParam(required = false) String numeroNota,
            @RequestParam(required = false) String lote,
            @RequestParam(required = false) LocalDate validade) {

        if (custo == null || custo.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("Custo inválido.");
        }

        try {
            service.entradaEstoque(ean, qtd, custo, numeroNota, lote, validade);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- 7. INTELIGÊNCIA DE PREÇO ---

    @GetMapping("/{id}/sugestao-preco")
    public ResponseEntity<SugestaoPrecoDTO> obterSugestao(@PathVariable Long id) {
        // Busca o produto
        var produto = repository.findById(id).orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        // AGORA VAI FUNCIONAR: O método existe na classe PrecificacaoService importada acima
        return ResponseEntity.ok(precificacaoService.calcularSugestao(produto));
    }

    @PatchMapping("/{id}/definir-preco")
    public ResponseEntity<Void> definirPreco(@PathVariable Long id, @RequestParam BigDecimal novoPreco) {
        var produto = repository.findById(id).orElseThrow(() -> new RuntimeException("Produto não encontrado"));
        produto.setPrecoVenda(novoPreco);
        repository.save(produto);
        return ResponseEntity.ok().build();
    }

    // --- 8. GESTÃO DE ATIVAÇÃO ---

    @DeleteMapping("/{ean}")
    public ResponseEntity<Void> inativar(@PathVariable String ean) {
        service.inativarPorEan(ean);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{ean}/ativar")
    public ResponseEntity<Void> reativar(@PathVariable String ean) {
        service.reativarPorEan(ean);
        return ResponseEntity.ok().build();
    }
}
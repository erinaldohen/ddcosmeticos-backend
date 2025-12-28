package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoPrecoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService; // INJETADO
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PrecificacaoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
// CORREÇÃO: service (singular)
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao.CosmosService;
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

    @Autowired private ProdutoService service;
    @Autowired private ProdutoRepository repository;
    @Autowired private CosmosService cosmosService;
    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private EstoqueService estoqueService; // Novo: Para movimentação

    // --- 1. LISTAGEM ---
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

    // --- 4. CADASTRO ---
    @PostMapping
    public ResponseEntity<?> cadastrar(@RequestBody @Valid ProdutoDTO dados) {
        try {
            // Mapeamento DTO -> Entidade
            Produto novoProduto = new Produto();
            novoProduto.setCodigoBarras(dados.codigoBarras());
            novoProduto.setDescricao(dados.descricao());
            novoProduto.setPrecoVenda(dados.precoVenda());
            novoProduto.setPrecoCusto(dados.precoCusto());
            novoProduto.setEstoqueMinimo(dados.estoqueMinimo());
            novoProduto.setNcm(dados.ncm());
            novoProduto.setQuantidadeEmEstoque(0); // Inicia zerado
            novoProduto.setAtivo(true);

            return ResponseEntity.status(HttpStatus.CREATED).body(service.salvar(novoProduto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- 5. ATUALIZAR ---
    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoDTO dados) {
        return ResponseEntity.ok(service.atualizar(id, dados));
    }

    // --- 6. ENTRADA DE ESTOQUE (Delegando para EstoqueService) ---
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
            // Monta o DTO que o EstoqueService espera
            EstoqueRequestDTO request = new EstoqueRequestDTO();
            request.setCodigoBarras(ean);
            request.setQuantidade(BigDecimal.valueOf(qtd));
            request.setPrecoCusto(custo);
            request.setNumeroNotaFiscal(numeroNota);
            // Definimos boleto/prazo padrão caso seja uma entrada avulsa rápida
            request.setFormaPagamento(FormaDePagamento.DINHEIRO);

            estoqueService.registrarEntrada(request);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao dar entrada: " + e.getMessage());
        }
    }

    // --- 7. INTELIGÊNCIA DE PREÇO ---
    @GetMapping("/{id}/sugestao-preco")
    public ResponseEntity<SugestaoPrecoDTO> obterSugestao(@PathVariable Long id) {
        var produto = repository.findById(id).orElseThrow(() -> new RuntimeException("Produto não encontrado"));
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
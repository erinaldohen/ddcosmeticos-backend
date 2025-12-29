package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoPrecoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ArquivoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PrecificacaoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao.CosmosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/produtos")
@Tag(name = "Produtos", description = "Gestão do catálogo de produtos e imagens")
public class ProdutoController {

    @Autowired private ProdutoService produtoService;
    @Autowired private ProdutoRepository repository;
    @Autowired private CosmosService cosmosService;
    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private EstoqueService estoqueService;
    @Autowired private ArquivoService arquivoService;

    // ==================================================================================
    // SESSÃO 1: LEITURA E BUSCA
    // ==================================================================================

    @GetMapping
    @Operation(summary = "Listar produtos", description = "Retorna todos os produtos ativos ou filtra por termo.")
    public ResponseEntity<List<Produto>> listar(@RequestParam(required = false) String termo) {
        return ResponseEntity.ok(produtoService.buscarInteligente(termo));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar produto por ID")
    public ResponseEntity<Produto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @GetMapping("/consulta-externa/{ean}")
    @Operation(summary = "Consulta API Cosmos", description = "Busca dados do produto por EAN em base externa.")
    public ResponseEntity<?> consultarExterno(@PathVariable String ean) {
        return cosmosService.consultarEan(ean)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/alerta-reposicao")
    @Operation(summary = "Alerta de Estoque Baixo")
    public ResponseEntity<List<Produto>> listarBaixoEstoque() {
        var todos = repository.findAll();
        var criticos = todos.stream()
                .filter(p -> p.getQuantidadeEmEstoque() <= (p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0))
                .toList();
        return ResponseEntity.ok(criticos);
    }

    // ==================================================================================
    // SESSÃO 2: CADASTRO E EDIÇÃO
    // ==================================================================================

    @PostMapping
    @Operation(summary = "Cadastrar produto")
    public ResponseEntity<ProdutoDTO> criar(@RequestBody @Valid ProdutoDTO dto) {
        ProdutoDTO novo = produtoService.salvar(dto);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(novo.id()).toUri();
        return ResponseEntity.created(uri).body(novo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar produto")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoDTO dto) {
        return ResponseEntity.ok(produtoService.atualizar(id, dto));
    }

    // ==================================================================================
    // SESSÃO 3: ESTOQUE E PRECIFICAÇÃO
    // ==================================================================================

    @PostMapping("/estoque")
    @Operation(summary = "Entrada Manual de Estoque")
    public ResponseEntity<?> adicionarEstoque(
            @RequestParam String ean,
            @RequestParam Integer qtd,
            @RequestParam BigDecimal custo,
            @RequestParam(required = false) String numeroNota,
            @RequestParam(required = false) String lote,
            @RequestParam(required = false) LocalDate validade)
    {
        if (custo == null || custo.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("Custo inválido.");
        }

        try {
            EstoqueRequestDTO request = new EstoqueRequestDTO();
            request.setCodigoBarras(ean);
            request.setQuantidade(BigDecimal.valueOf(qtd));
            request.setPrecoCusto(custo);
            request.setNumeroNotaFiscal(numeroNota);
            request.setNumeroLote(lote);
            request.setDataValidade(validade);
            request.setFormaPagamento(FormaDePagamento.DINHEIRO);

            estoqueService.registrarEntrada(request);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao dar entrada: " + e.getMessage());
        }
    }

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

    // ==================================================================================
    // SESSÃO 4: GESTÃO DE STATUS
    // ==================================================================================

    @DeleteMapping("/{ean}")
    @Operation(summary = "Inativar produto (Soft Delete)")
    public ResponseEntity<Void> inativar(@PathVariable String ean) {
        produtoService.inativarPorEan(ean);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================================
    // SESSÃO 5: UPLOAD DE IMAGEM
    // ==================================================================================
    @PostMapping(value = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de Foto", description = "Envia uma imagem (jpg/png) para o produto e atualiza a URL.")
    public ResponseEntity<Void> uploadImagem(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        // 1. Salva o arquivo no disco
        String nomeArquivo = arquivoService.salvarImagem(file);

        // 2. Gera a URL pública para acesso (Ex: /imagens/nome-do-arquivo.jpg)
        String urlAcesso = "/imagens/" + nomeArquivo;

        // 3. Atualiza o cadastro do produto com a nova URL
        produtoService.atualizarUrlImagem(id, urlAcesso);

        return ResponseEntity.ok().build();
    }
}
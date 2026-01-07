package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao.CosmosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
    @Autowired private CosmosService cosmosService;
    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private EstoqueService estoqueService;
    @Autowired private ArquivoService arquivoService;
    @Autowired private AuditoriaService auditoriaService;

    // --- 1. LEITURA ---

    @GetMapping
    @Operation(summary = "Listar produtos (Resumo)", description = "Retorna dados seguros para listagem (sem custo).")
    public ResponseEntity<Page<ProdutoListagemDTO>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) String descricao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "descricao") String sort) {

        String termoBusca = (termo != null && !termo.isBlank()) ? termo : descricao;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));

        // Retorna o DTO de Listagem (Protegido)
        return ResponseEntity.ok(produtoService.listarResumo(termoBusca, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar por ID")
    public ResponseEntity<Produto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @GetMapping("/analisar/{codigoBarras}")
    public ResponseEntity<AnalisePrecificacaoDTO> analisarIndividual(@PathVariable String codigoBarras) {
        try {
            return ResponseEntity.ok(precificacaoService.calcularSugestao(codigoBarras));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/consulta-externa/{ean}")
    public ResponseEntity<?> consultarExterno(@PathVariable String ean) {
        return cosmosService.consultarEan(ean)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/alerta-reposicao")
    public ResponseEntity<List<Produto>> listarBaixoEstoque() {
        return ResponseEntity.ok(produtoService.listarBaixoEstoque());
    }

    // --- 2. ESCRITA ---

    @PostMapping
    @Operation(summary = "Cadastrar Novo Produto")
    public ResponseEntity<?> cadastrar(@RequestBody @Valid ProdutoDTO dados) {
        try {
            // Delega a criação para o serviço
            ProdutoDTO salvo = produtoService.salvar(dados);

            URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                    .buildAndExpand(salvo.id()).toUri();
            return ResponseEntity.created(uri).body(salvo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoDTO dados) {
        return ResponseEntity.ok(produtoService.atualizar(id, dados));
    }

    @DeleteMapping("/{ean}")
    public ResponseEntity<Void> inativar(@PathVariable String ean) {
        produtoService.inativarPorEan(ean);
        return ResponseEntity.noContent().build();
    }

    // --- 3. ESTOQUE E PREÇO ---

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
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/sugestao-preco")
    public ResponseEntity<AnalisePrecificacaoDTO> obterSugestao(@PathVariable String codigoBarras) {
        try {
            return ResponseEntity.ok(precificacaoService.calcularSugestao(codigoBarras));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/definir-preco")
    public ResponseEntity<Void> definirPreco(@PathVariable Long id, @RequestParam BigDecimal novoPreco) {
        produtoService.definirPrecoVenda(id, novoPreco);
        return ResponseEntity.ok().build();
    }

    // --- 4. ARQUIVOS E IMAGENS ---

    @PostMapping(value = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadImagem(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        String nomeArquivo = arquivoService.salvarImagem(file);
        String urlAcesso = "/imagens/" + nomeArquivo;
        produtoService.atualizarUrlImagem(id, urlAcesso);
        return ResponseEntity.ok().build();
    }

    // --- 5. AUDITORIA ---

    @GetMapping("/{id}/historico")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistorico(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarHistorico(id));
    }

    @GetMapping("/lixeira")
    public ResponseEntity<List<Produto>> getLixeira() {
        return ResponseEntity.ok(auditoriaService.buscarLixeira());
    }

    @PutMapping("/{id}/restaurar")
    public ResponseEntity<Void> restaurar(@PathVariable Long id) {
        auditoriaService.restaurarProduto(id);
        return ResponseEntity.ok().build();
    }
}
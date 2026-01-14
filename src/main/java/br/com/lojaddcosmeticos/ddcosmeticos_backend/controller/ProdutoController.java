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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/produtos")
@Tag(name = "Produtos", description = "Gestão do catálogo de produtos e imagens")
public class ProdutoController {

    // --- DEPENDÊNCIAS ---
    @Autowired private ProdutoService produtoService;
    @Autowired private CosmosService cosmosService;
    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private EstoqueService estoqueService;
    @Autowired private ArquivoService arquivoService;
    @Autowired private AuditoriaService auditoriaService;
    @Autowired private ImpressaoService impressaoService;
    @Autowired private ImportacaoService importacaoService; // Injeção corrigida

    // ==================================================================================
    // 1. LEITURA E CONSULTAS (GET)
    // ==================================================================================

    @GetMapping
    @Operation(summary = "Listar produtos com paginação", description = "Busca produtos por termo ou descrição")
    public ResponseEntity<Page<ProdutoListagemDTO>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) String descricao,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "descricao") String sort) {

        String termoBusca = (termo != null && !termo.isBlank()) ? termo : descricao;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));

        return ResponseEntity.ok(produtoService.listarResumo(termoBusca, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar produto por ID")
    public ResponseEntity<Produto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @GetMapping("/analisar/{codigoBarras}")
    @Operation(summary = "Análise de precificação", description = "Sugere preço com base em regras e concorrentes")
    public ResponseEntity<AnalisePrecificacaoDTO> analisarIndividual(@PathVariable String codigoBarras) {
        try {
            return ResponseEntity.ok(precificacaoService.calcularSugestao(codigoBarras));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/consulta-externa/{ean}")
    @Operation(summary = "Consultar API Cosmos", description = "Busca dados do produto na base nacional de códigos de barras")
    public ResponseEntity<?> consultarExterno(@PathVariable String ean) {
        return cosmosService.consultarEan(ean)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/alerta-reposicao")
    @Operation(summary = "Produtos com estoque baixo")
    public ResponseEntity<List<Produto>> listarBaixoEstoque() {
        return ResponseEntity.ok(produtoService.listarBaixoEstoque());
    }

    @GetMapping("/proximo-sequencial")
    @Operation(summary = "Obter próximo sequencial", description = "Retorna o próximo número disponível para geração de EAN interno.")
    public ResponseEntity<Map<String, Object>> obterProximoSequencial() {
        return ResponseEntity.ok(Map.of("sequencial", System.currentTimeMillis()));
    }

    // ==================================================================================
    // 2. CADASTRO E EDIÇÃO (POST / PUT)
    // ==================================================================================

    @PostMapping
    @Operation(summary = "Cadastrar novo produto")
    public ResponseEntity<?> cadastrar(@RequestBody @Valid ProdutoDTO dados) {
        try {
            ProdutoDTO salvo = produtoService.salvar(dados);

            Long idSalvo = salvo.id(); // Record

            URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(idSalvo)
                    .toUri();

            return ResponseEntity.created(uri).body(salvo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar produto")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoDTO dados) {
        return ResponseEntity.ok(produtoService.atualizar(id, dados));
    }

    @PatchMapping("/{id}/definir-preco")
    @Operation(summary = "Alterar apenas o preço de venda")
    public ResponseEntity<Void> definirPreco(@PathVariable Long id, @RequestParam BigDecimal novoPreco) {
        produtoService.definirPrecoVenda(id, novoPreco);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/importar")
    @Operation(summary = "Importar CSV de produtos")
    public ResponseEntity<String> importarProdutos(@RequestParam("arquivo") MultipartFile arquivo) {
        importacaoService.importarProdutos(arquivo);
        return ResponseEntity.ok("Importação concluída com sucesso!");
    }

    // ==================================================================================
    // 3. MOVIMENTAÇÃO DE ESTOQUE (POST)
    // ==================================================================================

    @PostMapping("/estoque")
    @Operation(summary = "Registrar entrada de estoque")
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

    // ==================================================================================
    // 4. GESTÃO DE ARQUIVOS E IMAGENS
    // ==================================================================================

    @PostMapping(value = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de imagem do produto")
    public ResponseEntity<Void> uploadImagem(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        String nomeArquivo = arquivoService.salvarImagem(file);
        produtoService.atualizarUrlImagem(id, "/imagens/" + nomeArquivo);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/etiqueta")
    @Operation(summary = "Gerar código ZPL para etiqueta térmica")
    public ResponseEntity<String> gerarEtiqueta(@PathVariable Long id) {
        Produto produto = produtoService.buscarPorId(id);
        String etiqueta = impressaoService.gerarEtiquetaTermica(produto);
        return ResponseEntity.ok(etiqueta);
    }

    // ==================================================================================
    // 5. AUDITORIA E CICLO DE VIDA (DELETE / RESTORE)
    // ==================================================================================

    @GetMapping("/{id}/historico")
    @Operation(summary = "Ver histórico de alterações (Envers)")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistorico(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarHistorico(id));
    }

    @DeleteMapping("/{ean}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Inativar produto (Exclusão Lógica)")
    public ResponseEntity<Void> inativar(@PathVariable String ean) {
        produtoService.inativarPorEan(ean);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{ean}/reativar")
    @Operation(summary = "Reativar produto excluído")
    public ResponseEntity<Void> reativarProduto(@PathVariable String ean) {
        produtoService.reativarPorEan(ean);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/lixeira")
    @Operation(summary = "Listar produtos na lixeira")
    public ResponseEntity<List<Produto>> getLixeira() {
        return ResponseEntity.ok(auditoriaService.buscarLixeira());
    }

    @PutMapping("/{id}/restaurar")
    @Operation(summary = "Restaurar produto da lixeira pelo ID")
    public ResponseEntity<Void> restaurar(@PathVariable Long id) {
        auditoriaService.restaurarProduto(id);
        return ResponseEntity.ok().build();
    }

    // ==================================================================================
    // 6. FERRAMENTAS ADMINISTRATIVAS
    // ==================================================================================

    @PostMapping("/saneamento-fiscal")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Rodar saneamento fiscal em massa", description = "Reaplica regras tributárias em todos os produtos")
    public ResponseEntity<String> executarSaneamento() {
        return ResponseEntity.ok(produtoService.realizarSaneamentoFiscal());
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.audit.CustomRevisionEntity;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao.CosmosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity; // <--- CORREÇÃO: Usar Default
import org.hibernate.envers.RevisionType;          // <--- CORREÇÃO: Importar Tipo
import org.hibernate.envers.query.AuditEntity;     // <--- CORREÇÃO: Importar Query
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
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
    @Autowired private AuditoriaService auditoriaService;

    @Autowired private EntityManager entityManager;

    // ==================================================================================
    // SESSÃO 1: LEITURA E BUSCA
    // ==================================================================================
    @GetMapping("/analisar/{codigoBarras}")
    public ResponseEntity<AnalisePrecificacaoDTO> analisarIndividual(@PathVariable String codigoBarras) {
        try {
            AnalisePrecificacaoDTO analise = precificacaoService.calcularSugestao(codigoBarras);
            return ResponseEntity.ok(analise);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
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

    @GetMapping
    @Operation(summary = "Listar produtos com busca e paginação",
            description = "Retorna lista de produtos ativos. Permite filtrar por termo (nome/código) e paginação.")
    public ResponseEntity<Page<ProdutoDTO>> listar(
            @RequestParam(required = false) String busca,
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "descricao") Pageable pageable) {
        Page<ProdutoDTO> produtos = produtoService.listarTodos(busca, pageable);
        return ResponseEntity.ok(produtos);
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
    public ResponseEntity<AnalisePrecificacaoDTO> obterSugestao(@PathVariable String codigoBarras) {
        // Correção: Agora buscamos via service para garantir lógica única
        try {
            return ResponseEntity.ok(precificacaoService.calcularSugestao(codigoBarras));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
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
        String nomeArquivo = arquivoService.salvarImagem(file);
        String urlAcesso = "/imagens/" + nomeArquivo;
        produtoService.atualizarUrlImagem(id, urlAcesso);
        return ResponseEntity.ok().build();
    }

    // ==================================================================================
    // SESSÃO 6: AUDITORIA E HISTÓRICO (CORRIGIDO)
    // ==================================================================================

    @GetMapping("/{id}/historico")
    @Operation(summary = "Histórico de Alterações", description = "Lista quem alterou o produto e quando.")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistorico(@PathVariable Long id) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        // Busca as revisões
        List<Object[]> results = reader.createQuery()
                .forRevisionsOfEntity(Produto.class, false, true)
                .add(AuditEntity.id().eq(id))
                .getResultList();

        List<HistoricoProdutoDTO> historico = new ArrayList<>();

        for (Object[] row : results) {
            Produto pAntigo = (Produto) row[0];

            // --- AQUI ESTÁ A CORREÇÃO ---
            // Em vez de DefaultRevisionEntity, usamos a sua CustomRevisionEntity
            CustomRevisionEntity info = (CustomRevisionEntity) row[1];
            // ----------------------------

            RevisionType type = (RevisionType) row[2];

            String tipoString = switch (type) {
                case ADD -> "CRIADO";
                case MOD -> "ALTERADO";
                case DEL -> "EXCLUÍDO";
            };

            // Se a sua CustomRevisionEntity tiver um campo 'usuario',
            // você pode passá-lo aqui no lugar de info.getId().toString()
            historico.add(new HistoricoProdutoDTO(
                    info.getId(),                      // ID da revisão
                    new Date(info.getTimestamp()),     // Data
                    tipoString,                        // Tipo da ação
                    pAntigo.getDescricao(),            // Nome do produto na época
                    pAntigo.getPrecoVenda(),           // Preço na época
                    pAntigo.getPrecoCusto(),           // Custo na época
                    pAntigo.getQuantidadeEmEstoque()   // Estoque na época
            ));
        }

        historico.sort((a, b) -> b.getDataAlteracao().compareTo(a.getDataAlteracao()));
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/lixeira")
    @Operation(summary = "Ver Lixeira", description = "Lista produtos excluídos.")
    public ResponseEntity<List<Produto>> getLixeira() {
        return ResponseEntity.ok(auditoriaService.buscarLixeira());
    }

    @PutMapping("/{id}/restaurar")
    @Operation(summary = "Restaurar Produto", description = "Tira o produto da lixeira.")
    public ResponseEntity<Void> restaurar(@PathVariable Long id) {
        auditoriaService.restaurarProduto(id);
        return ResponseEntity.ok().build();
    }
}
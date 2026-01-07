package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.audit.CustomRevisionEntity;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao.CosmosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
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
    // 1. LEITURA (GET)
    // ==================================================================================

    @GetMapping
    @Operation(summary = "Listar produtos (Paginado)", description = "Busca por termo/descrição ou lista tudo.")
    public ResponseEntity<Page<Produto>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) String descricao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "descricao") String sort) {

        // Prioriza o 'termo', se não vier, tenta 'descricao'
        String termoBusca = (termo != null && !termo.isBlank()) ? termo : descricao;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
        return ResponseEntity.ok(produtoService.listarComFiltros(termoBusca, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar por ID")
    public ResponseEntity<Produto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @GetMapping("/analisar/{codigoBarras}")
    @Operation(summary = "Análise de Precificação")
    public ResponseEntity<AnalisePrecificacaoDTO> analisarIndividual(@PathVariable String codigoBarras) {
        try {
            AnalisePrecificacaoDTO analise = precificacaoService.calcularSugestao(codigoBarras);
            return ResponseEntity.ok(analise);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/consulta-externa/{ean}")
    @Operation(summary = "Consulta API Cosmos")
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
    // 2. ESCRITA (POST / PUT / DELETE)
    // ==================================================================================

    @PostMapping
    @Operation(summary = "Cadastrar Novo Produto")
    public ResponseEntity<?> cadastrar(@RequestBody @Valid ProdutoDTO dados) {
        try {
            // Conversão DTO -> Entidade
            Produto p = new Produto();
            p.setDescricao(dados.descricao());
            p.setCodigoBarras(dados.codigoBarras());
            p.setPrecoVenda(dados.precoVenda());
            p.setPrecoCusto(dados.precoCusto());
            p.setEstoqueMinimo(dados.estoqueMinimo());
            p.setNcm(dados.ncm());
            p.setQuantidadeEmEstoque(0);
            p.setAtivo(true);

            Produto salvo = produtoService.salvar(p);

            URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(salvo.getId()).toUri();
            return ResponseEntity.created(uri).body(salvo);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar Produto")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoDTO dados) {
        return ResponseEntity.ok(produtoService.atualizar(id, dados));
    }

    @DeleteMapping("/{ean}")
    @Operation(summary = "Inativar Produto")
    public ResponseEntity<Void> inativar(@PathVariable String ean) {
        produtoService.inativarPorEan(ean);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================================
    // 3. ESTOQUE E OPERAÇÕES
    // ==================================================================================

    @PostMapping("/estoque")
    @Operation(summary = "Entrada Manual de Estoque")
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
            return ResponseEntity.badRequest().body("Erro ao dar entrada: " + e.getMessage());
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
        var produto = repository.findById(id).orElseThrow(() -> new RuntimeException("Produto não encontrado"));
        produto.setPrecoVenda(novoPreco);
        repository.save(produto);
        return ResponseEntity.ok().build();
    }

    // ==================================================================================
    // 4. IMAGENS E ARQUIVOS
    // ==================================================================================

    @PostMapping(value = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de Foto")
    public ResponseEntity<Void> uploadImagem(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        String nomeArquivo = arquivoService.salvarImagem(file);
        String urlAcesso = "/imagens/" + nomeArquivo;
        produtoService.atualizarUrlImagem(id, urlAcesso);
        return ResponseEntity.ok().build();
    }

    // ==================================================================================
    // 5. AUDITORIA E LIXEIRA
    // ==================================================================================

    @GetMapping("/{id}/historico")
    @Operation(summary = "Histórico de Alterações")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistorico(@PathVariable Long id) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        List<Object[]> results = reader.createQuery()
                .forRevisionsOfEntity(Produto.class, false, true)
                .add(AuditEntity.id().eq(id))
                .getResultList();

        List<HistoricoProdutoDTO> historico = new ArrayList<>();

        for (Object[] row : results) {
            Produto pAntigo = (Produto) row[0];
            CustomRevisionEntity info = (CustomRevisionEntity) row[1];
            RevisionType type = (RevisionType) row[2];

            String tipoString = switch (type) {
                case ADD -> "CRIADO";
                case MOD -> "ALTERADO";
                case DEL -> "EXCLUÍDO";
            };

            historico.add(new HistoricoProdutoDTO(
                    info.getId(),
                    new Date(info.getTimestamp()),
                    tipoString,
                    pAntigo.getDescricao(),
                    pAntigo.getPrecoVenda(),
                    pAntigo.getPrecoCusto(),
                    pAntigo.getQuantidadeEmEstoque()
            ));
        }
        historico.sort((a, b) -> b.getDataAlteracao().compareTo(a.getDataAlteracao()));
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/lixeira")
    @Operation(summary = "Ver Lixeira")
    public ResponseEntity<List<Produto>> getLixeira() {
        return ResponseEntity.ok(auditoriaService.buscarLixeira());
    }

    @PutMapping("/{id}/restaurar")
    @Operation(summary = "Restaurar da Lixeira")
    public ResponseEntity<Void> restaurar(@PathVariable Long id) {
        auditoriaService.restaurarProduto(id);
        return ResponseEntity.ok().build();
    }
}
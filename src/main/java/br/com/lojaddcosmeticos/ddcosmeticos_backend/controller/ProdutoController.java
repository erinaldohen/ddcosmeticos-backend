package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/produtos")
@Tag(name = "Produtos", description = "Gestão de Catálogo, IA e Estoque")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    @GetMapping("/ncm/sugestoes")
    @Operation(summary = "Busca sugestões inteligentes de NCM baseadas no histórico")
    public ResponseEntity<List<Map<String, String>>> buscarSugestoesNcm(@RequestParam String termo) {
        if (termo == null || termo.length() < 2) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(produtoService.buscarNcmsInteligente(termo));
    }

    @GetMapping
    @Operation(summary = "Lista produtos com filtros avançados (Catálogo)")
    public ResponseEntity<Page<ProdutoListagemDTO>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String statusEstoque,
            @RequestParam(required = false) Boolean semImagem,
            @RequestParam(required = false) Boolean semNcm,
            @RequestParam(required = false) Boolean precoZero,
            @RequestParam(required = false) Boolean revisaoPendente,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("descricao"));
        return ResponseEntity.ok(produtoService.listarResumo(
                termo, marca, categoria, statusEstoque, semImagem, semNcm, precoZero, revisaoPendente, pageable
        ));
    }

    @GetMapping("/lixeira")
    @Operation(summary = "Lista produtos inativos")
    public ResponseEntity<List<Produto>> listarLixeira() {
        return ResponseEntity.ok(produtoService.buscarLixeira());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca produto por ID")
    public ResponseEntity<Produto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Busca a trilha de auditoria (Envers) do produto")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistorico(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarHistorico(id));
    }

    @GetMapping("/baixo-estoque")
    @Operation(summary = "Lista produtos com estoque crítico")
    public ResponseEntity<List<Produto>> listarBaixoEstoque() {
        return ResponseEntity.ok(produtoService.listarBaixoEstoque());
    }

    @GetMapping("/ean/{ean}")
    @Operation(summary = "Busca produto por EAN (Local ou API Externa)")
    public ResponseEntity<ProdutoDTO> buscarPorEan(@PathVariable String ean) {
        return ResponseEntity.ok(produtoService.buscarPorEanOuExterno(ean));
    }

    @GetMapping("/pdv")
    @Operation(summary = "Lista rápida de produtos para o Frente de Caixa (PDV)")
    public ResponseEntity<Page<ProdutoListagemDTO>> buscarParaPdv(
            @RequestParam(required = false) String termo,
            Pageable pageable) {
        return ResponseEntity.ok(produtoService.listarResumo(
                termo, null, null, null, false, false, false, false, pageable
        ));
    }

    @PostMapping
    @Operation(summary = "Cadastra novo produto")
    public ResponseEntity<ProdutoDTO> criar(@RequestBody ProdutoDTO dto) {
        return ResponseEntity.ok(produtoService.salvar(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza produto existente")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody ProdutoDTO dto) {
        return ResponseEntity.ok(produtoService.atualizar(id, dto));
    }

    @PatchMapping("/{id}/preco")
    @Operation(summary = "Atualização rápida do Preço de Venda")
    public ResponseEntity<Void> atualizarPreco(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Number novoPreco = (Number) payload.get("novoPreco");
        if (novoPreco != null) {
            produtoService.definirPrecoVenda(id, new BigDecimal(novoPreco.toString()));
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/custo")
    @Operation(summary = "Atualização rápida do Custo de Aquisição")
    public ResponseEntity<Void> atualizarCusto(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Number novoCusto = (Number) payload.get("novoCusto");
        if (novoCusto != null) {
            produtoService.definirPrecoCusto(id, new BigDecimal(novoCusto.toString()));
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{ean}")
    @Operation(summary = "Inativa um produto (Move para lixeira)")
    public ResponseEntity<Void> inativar(@PathVariable String ean) {
        produtoService.inativarPorEan(ean);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{ean}/reativar")
    @Operation(summary = "Reativa um produto da lixeira")
    public ResponseEntity<Void> reativarProduto(@PathVariable String ean) {
        produtoService.reativarPorEan(ean);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/analisar-ia")
    @Operation(summary = "Pede à IA para deduzir NCM e Categoria do Produto")
    public ResponseEntity<Map<String, String>> analisarProdutoIA(@RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(produtoService.analisarProdutoComIA(payload));
    }

    @PostMapping("/saneamento-fiscal")
    @Operation(summary = "Auditoria fiscal em massa (Recalcula tributos e SALVA no banco)")
    public ResponseEntity<Map<String, Object>> realizarSaneamento() {
        return ResponseEntity.ok(produtoService.saneamentoFiscal());
    }

    @PostMapping("/saneamento-custos")
    @Operation(summary = "Preenche custos zerados com base no preço de venda (Markup 100%)")
    public ResponseEntity<Map<String, Object>> realizarSaneamentoCustos() {
        return ResponseEntity.ok(produtoService.saneamentoCustos());
    }

    @PostMapping("/corrigir-ncms-ia")
    @Operation(summary = "Correção de NCMs usando Inteligência de Padrões")
    public ResponseEntity<Map<String, Object>> corrigirNcmsIA() {
        return ResponseEntity.ok(produtoService.corrigirNcmsEmMassa());
    }

    @PostMapping("/importar")
    @Operation(summary = "Importação de Catálogo via Planilha (Excel/CSV)")
    public ResponseEntity<Map<String, Object>> importarArquivo(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(produtoService.importarProdutos(arquivo));
    }

    @GetMapping("/exportar/csv")
    @Operation(summary = "Baixar todo o catálogo em formato CSV")
    public ResponseEntity<byte[]> exportarCsv() {
        byte[] dados = produtoService.gerarRelatorioCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estoque.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=ISO-8859-1"))
                .body(dados);
    }

    @GetMapping("/exportar/excel")
    @Operation(summary = "Baixar todo o catálogo em formato Excel (XLSX)")
    public ResponseEntity<byte[]> exportarExcel() {
        byte[] dados = produtoService.gerarRelatorioExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estoque.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(dados);
    }

    @GetMapping("/proximo-sequencial")
    @Operation(summary = "Gera o próximo código de barras interno disponível")
    public ResponseEntity<String> obterProximoSequencial() {
        return ResponseEntity.ok(produtoService.gerarProximoEanInterno());
    }

    @GetMapping("/alertas/pendentes-revisao")
    @Operation(summary = "Conta os produtos sinalizados com alertas de sistema")
    public ResponseEntity<Long> contarProdutosPendentes() {
        return ResponseEntity.ok(produtoService.obterRaioXInteligenciaArtificial().get("totalAnomalias") != null
                ? ((Number) produtoService.obterRaioXInteligenciaArtificial().get("totalAnomalias")).longValue() : 0L);
    }

    @GetMapping("/cross-sell")
    @Operation(summary = "Sugestões inteligentes de produtos complementares (Cross-Sell)")
    public ResponseEntity<List<Produto>> buscarSugestoesCrossSell(
            @RequestParam Long produtoBaseId,
            @RequestParam(defaultValue = "3") int limite) {
        return ResponseEntity.ok(produtoService.buscarSugestoesCrossSell(produtoBaseId, limite));
    }

    @PostMapping("/corrigir-eans-internos-ia")
    @Operation(summary = "Saneamento Matemático de EANs Internos (GS1)")
    public ResponseEntity<?> corrigirEansInternosIa() {
        try {
            return ResponseEntity.ok(produtoService.corrigirEansInternosIa());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("sucesso", false, "mensagem", "Erro na IA de EAN: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/{id}/etiqueta", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Gera o código de impressão térmica (ZPL) de um produto")
    public ResponseEntity<String> imprimirEtiqueta(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(produtoService.imprimirEtiqueta(id));
        } catch (Exception e) {
            log.error("Erro ao gerar etiqueta ZPL", e);
            return ResponseEntity.internalServerError().body("Erro ao gerar etiqueta.");
        }
    }

    @GetMapping("/dashboard-ia")
    @Operation(summary = "Traz o Raio-X completo das anomalias no Catálogo")
    public ResponseEntity<Map<String, Object>> obterRaioXIA() {
        return ResponseEntity.ok(produtoService.obterRaioXInteligenciaArtificial());
    }

    @PostMapping("/quick-fix-ia/{tipo}")
    @Operation(summary = "Aplica a resolução rápida de IA baseada no impasse")
    public ResponseEntity<Map<String, Object>> aplicarQuickFixIA(@PathVariable String tipo) {
        return ResponseEntity.ok(produtoService.aplicarQuickFixIA(tipo));
    }

    @GetMapping("/codigo/{ean}")
    @Operation(summary = "Busca ultrarrápida por código de barras (Frente de Loja)")
    public ResponseEntity<ProdutoDTO> buscarPorEanRapido(@PathVariable String ean) {
        ProdutoDTO prod = produtoService.buscarPorEanOuExterno(ean);
        return (prod != null && prod.id() != null) ? ResponseEntity.ok(prod) : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/divergencia")
    @Operation(summary = "Recebe alerta de preço errado via app/coletor de gôndola")
    public ResponseEntity<Void> reportarDivergencia(@PathVariable Long id) {
        produtoService.sinalizarDivergenciaGondola(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/divergencias-gondola")
    @Operation(summary = "Lista produtos sinalizados com divergência de preço física")
    public ResponseEntity<List<ProdutoListagemDTO>> listarDivergencias() {
        return ResponseEntity.ok(produtoService.listarDivergenciasGondola());
    }

    @PostMapping("/{id}/resolver-divergencia")
    @Operation(summary = "Resolve divergência e devolve ZPL da nova etiqueta")
    public ResponseEntity<Map<String, String>> resolverDivergencia(
            @PathVariable Long id, @RequestParam BigDecimal novoPreco) {
        return ResponseEntity.ok(produtoService.resolverDivergenciaEImprimir(id, novoPreco));
    }

    @PatchMapping("/{id}/preco-venda")
    @Operation(summary = "Modo Excel: Salva Preço Retalho rapidamente")
    public ResponseEntity<Void> atualizarPrecoVendaRapido(@PathVariable Long id, @RequestParam BigDecimal valor) {
        produtoService.definirPrecoVenda(id, valor);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/estoque")
    @Operation(summary = "Modo Excel: Salva Estoque Físico rapidamente")
    public ResponseEntity<Void> atualizarEstoqueRapido(@PathVariable Long id, @RequestParam Integer quantidade) {
        produtoService.ajustarEstoqueRapido(id, quantidade);
        return ResponseEntity.ok().build();
    }
}
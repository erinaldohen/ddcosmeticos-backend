package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
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

@RestController
@RequestMapping("/api/v1/produtos")
@Tag(name = "Produtos", description = "Gestão de Catálogo e Estoque")
@Slf4j
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @GetMapping("/ncm/sugestoes")
    public ResponseEntity<List<Map<String, String>>> buscarSugestoesNcm(@RequestParam String termo) {
        if (termo == null || termo.length() < 2) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(produtoService.buscarNcmsInteligente(termo));
    }

    @GetMapping
    @Operation(summary = "Lista produtos com filtros avançados")
    public ResponseEntity<Page<ProdutoListagemDTO>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String statusEstoque,
            @RequestParam(required = false) Boolean semImagem,
            @RequestParam(required = false) Boolean semNcm,
            @RequestParam(required = false) Boolean precoZero,
            @RequestParam(required = false) Boolean revisaoPendente, // 🚩 PARÂMETRO ADICIONADO PARA O FILTRO DO ALERTA
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("descricao"));

        // Passa todos os 8 parâmetros de filtro para o Service
        return ResponseEntity.ok(produtoService.listarResumo(
                termo, marca, categoria, statusEstoque, semImagem, semNcm, precoZero, revisaoPendente, pageable
        ));
    }

    @GetMapping("/lixeira")
    public ResponseEntity<List<Produto>> listarLixeira() {
        return ResponseEntity.ok(produtoService.buscarLixeira());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Produto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @GetMapping("/{id}/historico")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistorico(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarHistorico(id));
    }

    @GetMapping("/baixo-estoque")
    public ResponseEntity<List<Produto>> listarBaixoEstoque() {
        return ResponseEntity.ok(produtoService.listarBaixoEstoque());
    }

    @GetMapping("/ean/{ean}")
    public ResponseEntity<ProdutoDTO> buscarPorEan(@PathVariable String ean) {
        return ResponseEntity.ok(produtoService.buscarPorEanOuExterno(ean));
    }

    @GetMapping("/pdv")
    public ResponseEntity<Page<ProdutoListagemDTO>> buscarParaPdv(
            @RequestParam(required = false) String termo,
            Pageable pageable) {
        // Para a busca do PDV, a flag de revisaoPendente é 'false' ou irrelevante (por isso passamos null/false no final)
        return ResponseEntity.ok(produtoService.listarResumo(
                termo, null, null, null, false, false, false, false, pageable
        ));
    }

    // --- ESCRITA ---

    @PostMapping
    public ResponseEntity<ProdutoDTO> criar(@RequestBody ProdutoDTO dto) {
        return ResponseEntity.ok(produtoService.salvar(dto));
    }

    @PutMapping("/{id}")
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

    // NOVO: Endpoint para gestão rápida de compras
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
    @Operation(summary = "Inativa um produto (move para lixeira)")
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

    // --- FISCAL & INTELIGÊNCIA ---

    // 🚩 NOVO: Endpoint para o React enviar a descrição do produto para a IA analisar
    @PostMapping("/analisar-ia")
    @Operation(summary = "Pede à IA para deduzir NCM e Categoria do Produto")
    public ResponseEntity<Map<String, String>> analisarProdutoIA(@RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(produtoService.analisarProdutoComIA(payload));
    }

    @PostMapping("/saneamento-fiscal")
    @Operation(summary = "Recalcula tributos e SALVA no banco")
    public ResponseEntity<Map<String, Object>> realizarSaneamento() {
        return ResponseEntity.ok(produtoService.saneamentoFiscal());
    }

    // NOVO: Arruma o banco de dados para o Dashboard funcionar
    @PostMapping("/saneamento-custos")
    @Operation(summary = "Preenche custos zerados com base no preço de venda (Markup 100%)")
    public ResponseEntity<Map<String, Object>> realizarSaneamentoCustos() {
        return ResponseEntity.ok(produtoService.saneamentoCustos());
    }

    @PostMapping("/corrigir-ncms-ia")
    @Operation(summary = "Correção de NCMs usando Inteligência")
    public ResponseEntity<Map<String, Object>> corrigirNcmsIA() {
        return ResponseEntity.ok(produtoService.corrigirNcmsEmMassa());
    }

    // --- IMPORTAÇÃO E EXPORTAÇÃO ---

    @PostMapping("/importar")
    public ResponseEntity<Map<String, Object>> importarArquivo(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(produtoService.importarProdutos(arquivo));
    }

    @GetMapping("/exportar/csv")
    public ResponseEntity<byte[]> exportarCsv() {
        byte[] dados = produtoService.gerarRelatorioCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estoque.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=ISO-8859-1"))
                .body(dados);
    }

    @GetMapping("/exportar/excel")
    public ResponseEntity<byte[]> exportarExcel() {
        byte[] dados = produtoService.gerarRelatorioExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estoque.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(dados);
    }

    // --- UTILITÁRIOS ---

    @GetMapping("/proximo-sequencial")
    @Operation(summary = "Gera o próximo código de barras interno (começado com 2)")
    public ResponseEntity<String> obterProximoSequencial() {
        String proximoEan = produtoService.gerarProximoEanInterno();
        return ResponseEntity.ok(proximoEan);
    }

    @GetMapping("/alertas/pendentes-revisao")
    @Operation(summary = "Conta os produtos que precisam de revisão")
    public ResponseEntity<Long> contarProdutosPendentes() {
        long pendentes = produtoRepository.countProdutosPendentesDeRevisao();
        return ResponseEntity.ok(pendentes);
    }

    // =======================================================
    // 🔥 ADICIONE ESTE BLOCO AQUI (Antes do @GetMapping("/{id}"))
    // =======================================================
    @GetMapping("/cross-sell")
    @Operation(summary = "Sugestões inteligentes de produtos complementares (Cross-Sell)")
    public ResponseEntity<List<Produto>> buscarSugestoesCrossSell(
            @RequestParam Long produtoBaseId,
            @RequestParam(defaultValue = "3") int limite) {

        // Retorna as sugestões da IA ou produtos da mesma subcategoria
        return ResponseEntity.ok(produtoService.buscarSugestoesCrossSell(produtoBaseId, limite));
    }
    // =========================================================================
    // 🔥 ROTA DO ROBÔ DE EANs INTERNOS 🔥
    // =========================================================================
    @PostMapping("/corrigir-eans-internos-ia")
    public ResponseEntity<?> corrigirEansInternosIa() {
        try {
            return ResponseEntity.ok(produtoService.corrigirEansInternosIa());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "sucesso", false,
                    "mensagem", "Erro na IA de EAN: " + e.getMessage()
            ));
        }
    }
    // =========================================================================
    // 🔥 ENDPOINT DA IMPRESSORA TÉRMICA (ZPL) 🔥
    // =========================================================================
    /**
     * Retorna o código ZPL bruto (em texto) para impressão térmica.
     * Produz 'text/plain' para não causar problemas no navegador/frontend.
     */
    @GetMapping(value = "/{id}/etiqueta", produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    public org.springframework.http.ResponseEntity<String> imprimirEtiqueta(@PathVariable Long id) {
        try {
            String zpl = produtoService.imprimirEtiqueta(id);
            return org.springframework.http.ResponseEntity.ok(zpl);
        } catch (br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException e) {
            return org.springframework.http.ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erro ao gerar etiqueta ZPL para o produto ID: {}", id, e);
            return org.springframework.http.ResponseEntity.internalServerError().body("Erro ao gerar etiqueta.");
        }
    }
    // =========================================================================
    // 🔥 ROTAS DO DASHBOARD DE IA (RAIO-X E QUICK FIX)
    // =========================================================================

    @GetMapping("/dashboard-ia")
    public org.springframework.http.ResponseEntity<Map<String, Object>> obterRaioXIA() {
        return org.springframework.http.ResponseEntity.ok(produtoService.obterRaioXInteligenciaArtificial());
    }

    @PostMapping("/quick-fix-ia/{tipo}")
    public org.springframework.http.ResponseEntity<Map<String, Object>> aplicarQuickFixIA(@PathVariable String tipo) {
        return org.springframework.http.ResponseEntity.ok(produtoService.aplicarQuickFixIA(tipo));
    }
    // =========================================================================
    // 🔥 ROTAS DO MODO AUDITOR DE GÔNDOLA
    // =========================================================================

    // 1. Busca rápida por EAN (Otimizada para o leitor de código de barras)
    @GetMapping("/codigo/{ean}")
    public org.springframework.http.ResponseEntity<br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO> buscarPorEanRapido(@PathVariable String ean) {
        br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO prod = produtoService.buscarPorEanOuExterno(ean);
        if (prod != null && prod.id() != null) {
            return org.springframework.http.ResponseEntity.ok(prod);
        }
        return org.springframework.http.ResponseEntity.notFound().build();
    }

    // 2. Recebe o alerta do telemóvel e marca o produto como pendente
    @PostMapping("/{id}/divergencia")
    public org.springframework.http.ResponseEntity<Void> reportarDivergencia(@PathVariable Long id) {
        produtoService.sinalizarDivergenciaGondola(id);
        return org.springframework.http.ResponseEntity.ok().build();
    }
    @GetMapping("/divergencias-gondola")
    public org.springframework.http.ResponseEntity<List<br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO>> listarDivergencias() {
        return org.springframework.http.ResponseEntity.ok(produtoService.listarDivergenciasGondola());
    }

    @PostMapping("/{id}/resolver-divergencia")
    public org.springframework.http.ResponseEntity<Map<String, String>> resolverDivergencia(
            @PathVariable Long id,
            @RequestParam java.math.BigDecimal novoPreco) {
        return org.springframework.http.ResponseEntity.ok(produtoService.resolverDivergenciaEImprimir(id, novoPreco));
    }
    // =========================================================================
    // 🔥 ROTAS PARA EDIÇÃO INLINE (MODO EXCEL)
    // =========================================================================
    @PatchMapping("/{id}/preco-venda")
    public org.springframework.http.ResponseEntity<Void> atualizarPrecoVendaRapido(@PathVariable Long id, @RequestParam java.math.BigDecimal valor) {
        produtoService.definirPrecoVenda(id, valor);
        return org.springframework.http.ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/estoque")
    public org.springframework.http.ResponseEntity<Void> atualizarEstoqueRapido(@PathVariable Long id, @RequestParam Integer quantidade) {
        produtoService.ajustarEstoqueRapido(id, quantidade);
        return org.springframework.http.ResponseEntity.ok().build();
    }
}
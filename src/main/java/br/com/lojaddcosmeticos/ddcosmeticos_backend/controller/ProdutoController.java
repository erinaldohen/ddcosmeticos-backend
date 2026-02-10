package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    // --- LEITURA (MÉTODO UNIFICADO) ---

    @GetMapping
    public ResponseEntity<Page<ProdutoListagemDTO>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String statusEstoque,
            @RequestParam(required = false) Boolean semImagem,
            @RequestParam(required = false) Boolean semNcm,
            @RequestParam(required = false) Boolean precoZero,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("descricao"));

        // Chama o método do service passando todos os filtros.
        // Certifique-se que no ProdutoService existe este método com esta assinatura.
        return ResponseEntity.ok(produtoService.listarResumo(
                termo, marca, categoria, statusEstoque, semImagem, semNcm, precoZero, pageable
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

    // Endpoint específico para o PDV (mais leve)
    @GetMapping("/pdv")
    public ResponseEntity<Page<ProdutoListagemDTO>> buscarParaPdv(
            @RequestParam(required = false) String termo,
            Pageable pageable) {
        // Reutiliza a busca passando nulos nos outros filtros
        return ResponseEntity.ok(produtoService.listarResumo(
                termo, null, null, null, false, false, false, pageable
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
    public ResponseEntity<Void> atualizarPreco(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Number novoPreco = (Number) payload.get("novoPreco");
        if (novoPreco != null) {
            produtoService.definirPrecoVenda(id, new BigDecimal(novoPreco.toString()));
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

    @PostMapping("/saneamento-fiscal")
    @Operation(summary = "Recalcula tributos e SALVA no banco")
    public ResponseEntity<Map<String, Object>> realizarSaneamento() {
        return ResponseEntity.ok(produtoService.saneamentoFiscal());
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

    // CORREÇÃO CRÍTICA: Removido o método duplicado e mantido apenas este
    @GetMapping("/proximo-sequencial")
    @Operation(summary = "Gera o próximo código de barras interno (começado com 2)")
    public ResponseEntity<String> obterProximoSequencial() {
        String proximoEan = produtoService.gerarProximoEanInterno();
        return ResponseEntity.ok(proximoEan);
    }

}
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    // --- LEITURA ---
    @GetMapping
    public ResponseEntity<Page<ProdutoListagemDTO>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("descricao"));
        return ResponseEntity.ok(produtoService.listarResumo(termo, pageable));
    }

    @GetMapping("/resumo")
    public ResponseEntity<Page<ProdutoListagemDTO>> listarResumo(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // Redireciona para o método principal para manter padrão
        return listar(termo, page, size);
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
            produtoService.definirPrecoVenda(id, new java.math.BigDecimal(novoPreco.toString()));
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{ean}")
    public ResponseEntity<Void> inativar(@PathVariable String ean) {
        produtoService.inativarPorEan(ean);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{ean}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable String ean) {
        produtoService.reativarPorEan(ean);
        return ResponseEntity.noContent().build();
    }

    // --- FISCAL ---
    @PostMapping("/saneamento-fiscal")
    @Operation(summary = "Aplica regras fiscais (NCM, CST) automaticamente")
    public ResponseEntity<Map<String, Object>> realizarSaneamento() {
        return produtoService.realizarSaneamentoFiscal();
    }

    // --- IMPORTAÇÃO E EXPORTAÇÃO (CORRIGIDO: Apenas 1 endpoint para importar) ---

    @PostMapping("/importar")
    @Operation(summary = "Importa produtos via CSV ou Excel")
    public ResponseEntity<String> importarArquivo(@RequestParam("arquivo") MultipartFile arquivo) {
        // Chama o método roteador que criamos no Service
        String resultado = produtoService.importarProdutos(arquivo);
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/exportar/csv")
    @Operation(summary = "Baixa o estoque atual em CSV")
    public ResponseEntity<byte[]> exportarCsv() {
        byte[] dados = produtoService.gerarRelatorioCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estoque.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=ISO-8859-1"))
                .body(dados);
    }

    @GetMapping("/exportar/excel")
    @Operation(summary = "Baixa o estoque atual em Excel (XLSX)")
    public ResponseEntity<byte[]> exportarExcel() {
        byte[] dados = produtoService.gerarRelatorioExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estoque.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(dados);
    }
}
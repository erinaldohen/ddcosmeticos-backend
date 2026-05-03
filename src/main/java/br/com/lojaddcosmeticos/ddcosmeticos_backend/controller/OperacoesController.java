package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/operacoes")
@Tag(name = "Operações e Logística", description = "Ferramentas de Impressão Térmica de Gôndola e Relatórios Operacionais")
public class OperacoesController {

    @Autowired private RelatorioService relatorioService;
    @Autowired private EstoqueService estoqueService;
    @Autowired private ProdutoRepository produtoRepository;

    @GetMapping("/relatorio-compras/pdf")
    @Operation(summary = "Gerar Lista de Compras de Reposição (PDF)", description = "Analisa estoques críticos e gera uma folha de pedido para fornecedores (Mapeado por IA).")
    public ResponseEntity<byte[]> baixarListaComprasPdf() {
        List<Produto> produtosBaixoEstoque = estoqueService.gerarSugestaoCompras();
        List<SugestaoCompraDTO> sugestoes = produtosBaixoEstoque.stream().map(this::converterParaDTO).collect(Collectors.toList());
        byte[] pdfBytes = relatorioService.gerarPdfSugestaoCompras(sugestoes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Folha_Lista_Compras_Aprovadas.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/etiqueta/{codigoBarras}")
    @Operation(summary = "Gerar Etiqueta de Gôndola (Raw)", description = "Retorna o RAW Text (ZPL) formatado para impressoras térmicas baseadas no padrão Zebra/Epson.")
    public ResponseEntity<String> gerarEtiqueta(@PathVariable String codigoBarras) {
        Produto p = produtoRepository.findByCodigoBarras(codigoBarras).orElseThrow(() -> new RuntimeException("EAN não localizado na base."));
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(relatorioService.gerarEtiquetaTermica(p));
    }

    // Helper de conversão direta.
    private SugestaoCompraDTO converterParaDTO(Produto produto) {
        int min = produto.getEstoqueMinimo() != null ? produto.getEstoqueMinimo() : 0;
        int atual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0;
        int sugestao = Math.max(min - atual, 0);

        BigDecimal custoEstimado = (produto.getPrecoCusto() != null && sugestao > 0)
                ? produto.getPrecoCusto().multiply(new BigDecimal(sugestao))
                : BigDecimal.ZERO;

        String urgencia = (atual == 0) ? "CRÍTICO (ZERADO)" : (atual <= (min / 2)) ? "ALTA" : "NORMAL";

        return new SugestaoCompraDTO(
                produto.getCodigoBarras(), produto.getDescricao(), produto.getMarca(),
                atual, min, sugestao, urgencia, custoEstimado
        );
    }
}
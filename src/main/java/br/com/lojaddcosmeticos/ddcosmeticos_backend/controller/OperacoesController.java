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
@Tag(name = "Operações de Loja", description = "Módulo de PDV, Impressão e Relatórios Operacionais")
public class OperacoesController {

    @Autowired private RelatorioService relatorioService;
    @Autowired private EstoqueService estoqueService;
    @Autowired private ProdutoRepository produtoRepository;

    // ==================================================================================
    // SESSÃO 2: RELATÓRIOS GERENCIAIS (PDF)
    // ==================================================================================

    @GetMapping("/relatorio-compras/pdf")
    @Operation(summary = "Gerar Lista de Compras (PDF)", description = "Analisa estoque baixo e gera PDF para fornecedores.")
    public ResponseEntity<byte[]> baixarListaComprasPdf() {
        // 1. Busca a inteligência do estoque (Retorna List<Produto>)
        List<Produto> produtosBaixoEstoque = estoqueService.gerarSugestaoCompras();

        // 2. Converte Produtos para DTOs (Mapeamento)
        List<SugestaoCompraDTO> sugestoes = produtosBaixoEstoque.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());

        // 3. Transforma em binário do PDF
        byte[] pdfBytes = relatorioService.gerarPdfSugestaoCompras(sugestoes);

        // 4. Retorna como download
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lista_compras.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ==================================================================================
    // SESSÃO 3: SAÍDA PARA HARDWARE (Impressoras Térmicas)
    // ==================================================================================

    @GetMapping("/etiqueta/{codigoBarras}")
    @Operation(summary = "Gerar Etiqueta de Gôndola", description = "Retorna texto puro formatado para impressoras térmicas.")
    public ResponseEntity<String> gerarEtiqueta(@PathVariable String codigoBarras) {
        Produto p = produtoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado para impressão"));

        String textoTermico = relatorioService.gerarEtiquetaTermica(p);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(textoTermico);
    }

    // --- Método Auxiliar de Conversão (CORRIGIDO PARA RECORD) ---
    private SugestaoCompraDTO converterParaDTO(Produto produto) {
        // 1. Cálculos de Estoque
        int min = produto.getEstoqueMinimo() != null ? produto.getEstoqueMinimo() : 0;
        int atual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0;

        // Calcula a sugestão (se atual > min, sugestão é 0)
        int sugestao = Math.max(min - atual, 0);

        // 2. Cálculo de Custo Estimado (Novo campo do Record)
        BigDecimal custoEstimado = BigDecimal.ZERO;
        if (produto.getPrecoCusto() != null && sugestao > 0) {
            custoEstimado = produto.getPrecoCusto().multiply(new BigDecimal(sugestao));
        }

        // 3. Definição de Urgência (Novo campo do Record)
        String urgencia = "NORMAL";
        if (atual == 0) {
            urgencia = "CRÍTICO (ZERADO)";
        } else if (atual <= (min / 2)) {
            urgencia = "ALTA";
        }

        // 4. Instanciação via Construtor (Records não têm setters)
        return new SugestaoCompraDTO(
                produto.getCodigoBarras(),
                produto.getDescricao(),
                produto.getMarca(),
                atual,
                min,
                sugestao,
                urgencia,      // Campo novo obrigatório
                custoEstimado  // Campo novo obrigatório
        );
    }
}
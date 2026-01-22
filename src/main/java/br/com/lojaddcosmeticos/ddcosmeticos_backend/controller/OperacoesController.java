package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/operacoes")
@Tag(name = "Operações de Loja", description = "Módulo de PDV, Impressão e Relatórios Operacionais")
public class OperacoesController {

    @Autowired private RelatorioService relatorioService;
    @Autowired private EstoqueService estoqueService;
    @Autowired private VendaService vendaService;
    @Autowired private ProdutoRepository produtoRepository;

    // ==================================================================================
    // SESSÃO 1: PDV (Ponto de Venda e Baixa de Estoque)
    // ==================================================================================



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

        // Retorna TEXT_PLAIN para que o driver da impressora ou o Frontend receba o texto cru
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(textoTermico);
    }

    // --- Método Auxiliar de Conversão ---
    private SugestaoCompraDTO converterParaDTO(Produto p) {
        SugestaoCompraDTO dto = new SugestaoCompraDTO();
        dto.setCodigoBarras(p.getCodigoBarras());
        dto.setDescricao(p.getDescricao());
        dto.setMarca(p.getMarca());
        dto.setEstoqueAtual(p.getQuantidadeEmEstoque());
        dto.setEstoqueMinimo(p.getEstoqueMinimo());

        // Calcula quanto precisa comprar para atingir o mínimo (Margem de segurança)
        int min = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0;
        int atual = p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : 0;
        int sugestao = (min - atual) > 0 ? (min - atual) : 0;

        dto.setQuantidadeSugerida(sugestao);

        return dto;
    }
}
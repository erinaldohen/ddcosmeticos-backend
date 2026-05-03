package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.DocumentoFiscalPdfService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueIntelligenceService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/estoque")
@Tag(name = "Estoque Físico e Recebimento", description = "Gestão de Entradas, Notas e Inteligência de Curva ABC")
public class EstoqueController {

    @Autowired private EstoqueIntelligenceService estoqueIntelligenceService;
    @Autowired private EstoqueService estoqueService;
    @Autowired private DocumentoFiscalPdfService documentoFiscalPdfService;

    @GetMapping("/sugestao-compras")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Relatório Inteligente de Sugestão de Compras (Curva ABC)")
    public ResponseEntity<List<SugestaoCompraDTO>> getSugestaoCompras() {
        List<SugestaoCompraDTO> sugestoes = estoqueIntelligenceService.gerarRelatorioCompras();
        return sugestoes.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(sugestoes);
    }

    @PostMapping(value = "/importar-xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ESTOQUISTA', 'GERENTE')")
    @Operation(summary = "Processar NF-e de Fornecedor (XML)", description = "Lê XML da SEFAZ e devolve o mapa de dados estruturado para a tela.")
    public ResponseEntity<RetornoImportacaoXmlDTO> importarXml(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(estoqueService.processarXmlNotaFiscal(arquivo));
    }

    @PostMapping("/entrada")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESTOQUISTA', 'GERENTE')")
    @Operation(summary = "Efetivar Entrada de Mercadoria", description = "Processa o lote de itens, cria novos produtos, ajusta inventário físico e projeta Contas a Pagar.")
    public ResponseEntity<Void> finalizarEntrada(@RequestBody @Valid EntradaEstoqueDTO dto) {
        estoqueService.processarEntradaEmLote(dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/historico-entradas")
    @Operation(summary = "Log de Notas de Entrada Fechadas")
    public ResponseEntity<Page<HistoricoEntradaDTO>> listarHistoricoEntradas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(estoqueService.listarHistoricoEntradas(pageable));
    }

    @GetMapping("/historico-entradas/{numeroNota}/itens")
    @Operation(summary = "Ver itens da Nota de Entrada")
    public ResponseEntity<List<MovimentoEstoqueDTO>> detalharNota(@PathVariable String numeroNota) {
        return ResponseEntity.ok(estoqueService.buscarDetalhesNota(numeroNota));
    }

    @GetMapping(value = "/historico-entradas/{numeroNota}/danfe-oficial", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ESTOQUISTA')")
    @Operation(summary = "Gerar 2ª Via da DANFE A4 em PDF")
    public ResponseEntity<byte[]> baixarDanfeOficial(@PathVariable String numeroNota) {
        try {
            byte[] pdfBytes = documentoFiscalPdfService.gerarDanfePdf(numeroNota);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"DANFE_" + numeroNota + ".pdf\"")
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
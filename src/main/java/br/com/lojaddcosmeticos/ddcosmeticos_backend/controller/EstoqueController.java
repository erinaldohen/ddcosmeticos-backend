package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EntradaEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RetornoImportacaoXmlDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueIntelligenceService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/estoque")
@Tag(name = "Estoque", description = "Gestão de Entradas e Inteligência de Estoque")
public class EstoqueController {

    @Autowired
    private EstoqueIntelligenceService estoqueIntelligenceService;

    @Autowired
    private EstoqueService estoqueService;

    // --- 1. RELATÓRIO DE COMPRAS (MANTIDO) ---
    @GetMapping("/sugestao-compras")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Relatório de sugestão de compras (BI)")
    public ResponseEntity<List<SugestaoCompraDTO>> getSugestaoCompras() {
        List<SugestaoCompraDTO> sugestoes = estoqueIntelligenceService.gerarRelatorioCompras();

        if (sugestoes.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(sugestoes);
    }

    // --- 2. IMPORTAÇÃO DE XML (MANTIDO) ---
    @PostMapping(value = "/importar-xml", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESTOQUISTA', 'GERENTE')")
    @Operation(summary = "Importar NFe (XML)", description = "Lê XML da SEFAZ e retorna dados para pré-preenchimento")
    public ResponseEntity<RetornoImportacaoXmlDTO> importarXml(@RequestParam("arquivo") MultipartFile arquivo) {
        RetornoImportacaoXmlDTO dados = estoqueService.processarXmlNotaFiscal(arquivo);
        return ResponseEntity.ok(dados);
    }

    // --- 3. ENTRADA DE MERCADORIA (UNIFICADO E CORRIGIDO) ---
    // Removemos o antigo 'registrarEntrada' que causava conflito.
    // Mantivemos a segurança e documentação, mas usando o DTO novo (Lote).
    @PostMapping("/entrada")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESTOQUISTA', 'GERENTE')")
    @Operation(summary = "Registrar entrada de mercadoria", description = "Processa lista de itens, cria produtos novos, atualiza estoque e gera financeiro")
    public ResponseEntity<Void> finalizarEntrada(@RequestBody @Valid EntradaEstoqueDTO dto) {
        // Chama o método novo que suporta criação automática e financeiro
        estoqueService.processarEntradaEmLote(dto);
        return ResponseEntity.ok().build();
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
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
@RequestMapping("/api/v1/estoque") // Padronizado para v1
@Tag(name = "Estoque", description = "Gestão de Entradas e Inteligência de Estoque")
public class EstoqueController {

    // Serviço existente (Mantido)
    @Autowired
    private EstoqueIntelligenceService estoqueIntelligenceService;

    // Novo Serviço (Para processar a entrada e média ponderada)
    @Autowired
    private EstoqueService estoqueService;

    // --- 1. ENDPOINT EXISTENTE (MANTIDO) ---
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

    // --- 2. NOVO ENDPOINT (PARA A TELA DE ENTRADA) ---
    @PostMapping("/entrada")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESTOQUISTA', 'GERENTE')")
    @Operation(summary = "Registrar entrada de mercadoria", description = "Atualiza estoque e recalcula preço médio (Custo)")
    public ResponseEntity<Void> registrarEntrada(@RequestBody @Valid EstoqueRequestDTO dto) {
        // Chama o método que criamos no passo anterior
        estoqueService.registrarEntrada(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/importar-xml", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'ESTOQUISTA', 'GERENTE')")
    @Operation(summary = "Importar NFe (XML)", description = "Lê XML da SEFAZ e retorna dados para pré-preenchimento")
    public ResponseEntity<RetornoImportacaoXmlDTO> importarXml(@RequestParam("arquivo") MultipartFile arquivo) {
        RetornoImportacaoXmlDTO dados = estoqueService.processarXmlNotaFiscal(arquivo);
        return ResponseEntity.ok(dados);
    }
}
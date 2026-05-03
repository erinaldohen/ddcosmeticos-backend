package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioComissaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/relatorios")
@Tag(name = "Relatórios e BI", description = "Painéis de Inteligência e PDF Generativo")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;

    // Helper Limpo para Extração Segura de Datas do Frontend
    private LocalDate parseDataSegura(String dataRaw) {
        if (dataRaw == null || dataRaw.trim().isEmpty() || dataRaw.equals("undefined") || dataRaw.equals("null")) {
            return null;
        }
        return LocalDate.parse(dataRaw);
    }

    private Long parseVendedorId(String idRaw) {
        if (idRaw == null || idRaw.trim().isEmpty() || idRaw.equals("undefined") || idRaw.equals("null") || idRaw.equals("0")) {
            return null;
        }
        try { return Long.parseLong(idRaw); } catch (NumberFormatException e) { return null; }
    }

    // =========================================================================
    // 1. ROTAS DO PAINEL DE INTELIGÊNCIA (BI)
    // =========================================================================

    @GetMapping("/vendas")
    @Operation(summary = "Gerar DTO de Relatório de Vendas (BI)")
    public ResponseEntity<?> getRelatorioVendas(@RequestParam(required = false) String inicio, @RequestParam(required = false) String fim) {
        try {
            RelatorioVendasDTO relatorio = relatorioService.gerarRelatorioVendas(parseDataSegura(inicio), parseDataSegura(fim));
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            log.error("[BI VENDAS] Erro interno: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/estoque")
    @Operation(summary = "Gerar Dashboard de Estoque e Compras (BI)")
    public ResponseEntity<?> getRelatorioEstoque(@RequestParam(required = false) String inicio, @RequestParam(required = false) String fim) {
        try {
            Map<String, Object> relatorio = relatorioService.gerarRelatorioEstoque(parseDataSegura(inicio), parseDataSegura(fim));
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            log.error("[BI ESTOQUE] Erro interno: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/financeiro")
    @Operation(summary = "Gerar DRE e Fluxo de Caixa (BI)")
    public ResponseEntity<?> getRelatorioFinanceiro(@RequestParam(required = false) String inicio, @RequestParam(required = false) String fim) {
        try {
            Map<String, Object> relatorio = relatorioService.gerarRelatorioFinanceiro(parseDataSegura(inicio), parseDataSegura(fim));
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            log.error("[BI FINANCEIRO] Erro interno: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/fiscal")
    @Operation(summary = "Gerar Projeções de Simples Nacional (BI)")
    public ResponseEntity<?> getRelatorioFiscal(@RequestParam(required = false) String inicio, @RequestParam(required = false) String fim) {
        try {
            Map<String, Object> relatorio = relatorioService.gerarRelatorioFiscal(parseDataSegura(inicio), parseDataSegura(fim));
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            log.error("[BI FISCAL] Erro interno: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================================
    // 2. COMISSÕES E PDF
    // =========================================================================

    @GetMapping("/comissoes")
    @Operation(summary = "Calcular Folha de Comissões de Vendedores")
    public ResponseEntity<?> buscarComissoes(
            @RequestParam String dataInicio,
            @RequestParam String dataFim,
            @RequestParam(required = false) String vendedorId) {
        try {
            LocalDateTime inicioData = LocalDate.parse(dataInicio).atStartOfDay();
            LocalDateTime fimData = LocalDate.parse(dataFim).atTime(LocalTime.MAX);
            RelatorioComissaoDTO relatorio = relatorioService.gerarRelatorioComissoes(inicioData, fimData, parseVendedorId(vendedorId));
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            log.error("[COMISSOES] Falha ao processar relatório: ", e);
            return ResponseEntity.badRequest().body("Erro ao processar parâmetros do relatório.");
        }
    }

    @GetMapping("/comissoes/pdf")
    @Operation(summary = "Baixar Folha de Comissões assinável (PDF)")
    public ResponseEntity<?> baixarPdfComissoes(
            @RequestParam String dataInicio,
            @RequestParam String dataFim,
            @RequestParam(required = false) String vendedorId) {
        try {
            LocalDateTime inicioData = LocalDate.parse(dataInicio).atStartOfDay();
            LocalDateTime fimData = LocalDate.parse(dataFim).atTime(LocalTime.MAX);
            RelatorioComissaoDTO dados = relatorioService.gerarRelatorioComissoes(inicioData, fimData, parseVendedorId(vendedorId));
            byte[] pdf = relatorioService.gerarPdfComissoes(dados);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=comissoes_ddcosmeticos.pdf")
                    .body(pdf);
        } catch (Exception e) {
            log.error("Erro ao processar PDF de Comissões: ", e);
            return ResponseEntity.internalServerError().body("Erro: " + e.getMessage());
        }
    }

    // =========================================================================
    // 3. DOSSIÊ EXECUTIVO 360º E EARNINGS
    // =========================================================================

    @GetMapping("/dossie-executivo/pdf")
    @Operation(summary = "Gerar Dossiê Gerencial com síntese textual de IA (PDF)")
    public ResponseEntity<byte[]> baixarDossieExecutivoPdf(@RequestParam(required = false) String inicio, @RequestParam(required = false) String fim) {
        try {
            LocalDate dtInicio = parseDataSegura(inicio) != null ? parseDataSegura(inicio) : LocalDate.now().withDayOfMonth(1);
            LocalDate dtFim = parseDataSegura(fim) != null ? parseDataSegura(fim) : LocalDate.now();
            byte[] pdfBytes = relatorioService.gerarDossieExecutivoPdf(dtInicio, dtFim);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=Dossie_Executivo_DD.pdf")
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Erro Crítico ao gerar Dossiê Executivo: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/balanco-trimestral/pdf")
    @Operation(summary = "Gerar Balanço Oficial para Diretoria (PDF)")
    public ResponseEntity<byte[]> baixarBalancoTrimestral(@RequestParam int ano, @RequestParam int trimestre) {
        try {
            byte[] pdfBytes = relatorioService.gerarBalancoTrimestralPdf(ano, trimestre);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=Balanco_DD_Cosmeticos_" + trimestre + "T" + ano + ".pdf")
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Erro ao gerar Balanço Trimestral: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/teste-email", produces = "text/plain;charset=UTF-8")
    @Operation(summary = "Forçar envio manual do Dossiê Mensal por Email")
    public String dispararTesteManual() {
        try {
            relatorioService.dispararRelatorioMensalTeste();
            return "✅ Comando enviado com sucesso! Verifique o console do Spring Boot e a sua caixa de e-mail.";
        } catch (Exception e) {
            return "❌ Erro ao gerar o relatório: " + e.getMessage();
        }
    }
}
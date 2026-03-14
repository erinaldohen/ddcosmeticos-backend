package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioComissaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;

    // =========================================================================
    // 1. ROTAS DO PAINEL DE INTELIGÊNCIA (BI)
    // =========================================================================

    @GetMapping("/vendas")
    public ResponseEntity<?> getRelatorioVendas(HttpServletRequest request) {
        try {
            String inicioRaw = request.getParameter("inicio");
            String fimRaw = request.getParameter("fim");

            LocalDate inicio = (inicioRaw != null && !inicioRaw.isEmpty()) ? LocalDate.parse(inicioRaw) : null;
            LocalDate fim = (fimRaw != null && !fimRaw.isEmpty()) ? LocalDate.parse(fimRaw) : null;

            RelatorioVendasDTO relatorio = relatorioService.gerarRelatorioVendas(inicio, fim);
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            log.error("[BI VENDAS] Erro interno: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/estoque")
    public ResponseEntity<?> getRelatorioEstoque(HttpServletRequest request) {
        try {
            String inicioRaw = request.getParameter("inicio");
            String fimRaw = request.getParameter("fim");

            LocalDate inicio = (inicioRaw != null && !inicioRaw.isEmpty()) ? LocalDate.parse(inicioRaw) : null;
            LocalDate fim = (fimRaw != null && !fimRaw.isEmpty()) ? LocalDate.parse(fimRaw) : null;

            Map<String, Object> relatorio = relatorioService.gerarRelatorioEstoque(inicio, fim);
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            log.error("[BI ESTOQUE] Erro interno: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/financeiro")
    public ResponseEntity<?> getRelatorioFinanceiro(HttpServletRequest request) {
        try {
            String inicioRaw = request.getParameter("inicio");
            String fimRaw = request.getParameter("fim");

            LocalDate inicio = (inicioRaw != null && !inicioRaw.isEmpty()) ? LocalDate.parse(inicioRaw) : null;
            LocalDate fim = (fimRaw != null && !fimRaw.isEmpty()) ? LocalDate.parse(fimRaw) : null;

            Map<String, Object> relatorio = relatorioService.gerarRelatorioFinanceiro(inicio, fim);
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            log.error("[BI FINANCEIRO] Erro interno: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/fiscal")
    public ResponseEntity<?> getRelatorioFiscal(HttpServletRequest request) {
        try {
            String inicioRaw = request.getParameter("inicio");
            String fimRaw = request.getParameter("fim");

            LocalDate inicio = (inicioRaw != null && !inicioRaw.isEmpty()) ? LocalDate.parse(inicioRaw) : null;
            LocalDate fim = (fimRaw != null && !fimRaw.isEmpty()) ? LocalDate.parse(fimRaw) : null;

            Map<String, Object> relatorio = relatorioService.gerarRelatorioFiscal(inicio, fim);
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            log.error("[BI FISCAL] Erro interno: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================================
    // 2. COMISSÕES E PDF (Mantidos e protegidos)
    // =========================================================================

    @GetMapping("/comissoes")
    public ResponseEntity<?> buscarComissoes(HttpServletRequest request) {
        String dataInicioRaw = request.getParameter("dataInicio");
        String dataFimRaw = request.getParameter("dataFim");
        String vendedorIdRaw = request.getParameter("vendedorId");

        if (dataInicioRaw == null || dataFimRaw == null) {
            return ResponseEntity.badRequest().body("Parâmetros de data são obrigatórios.");
        }

        try {
            LocalDateTime inicio = LocalDate.parse(dataInicioRaw).atStartOfDay();
            LocalDateTime fim = LocalDate.parse(dataFimRaw).atTime(LocalTime.MAX);
            Long idVendedor = parseVendedorId(vendedorIdRaw);

            RelatorioComissaoDTO relatorio = relatorioService.gerarRelatorioComissoes(inicio, fim, idVendedor);
            return ResponseEntity.ok(relatorio);

        } catch (Exception e) {
            log.error("[COMISSOES] Falha ao processar relatório: ", e);
            return ResponseEntity.internalServerError().body("Erro ao processar relatório.");
        }
    }

    @GetMapping("/comissoes/pdf")
    public ResponseEntity<?> baixarPdfComissoes(HttpServletRequest request) {
        try {
            String dataInicioRaw = request.getParameter("dataInicio");
            String dataFimRaw = request.getParameter("dataFim");
            String vendedorIdRaw = request.getParameter("vendedorId");

            LocalDateTime inicio = LocalDate.parse(dataInicioRaw).atStartOfDay();
            LocalDateTime fim = LocalDate.parse(dataFimRaw).atTime(LocalTime.MAX);
            Long idVendedor = parseVendedorId(vendedorIdRaw);

            RelatorioComissaoDTO dados = relatorioService.gerarRelatorioComissoes(inicio, fim, idVendedor);
            byte[] pdf = relatorioService.gerarPdfComissoes(dados);

            if (pdf == null || pdf.length == 0) {
                return ResponseEntity.internalServerError().body("Falha ao gerar conteúdo do PDF.");
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=comissoes_ddcosmeticos.pdf")
                    .body(pdf);

        } catch (Exception e) {
            log.error("Erro ao processar PDF: ", e);
            return ResponseEntity.internalServerError().body("Erro: " + e.getMessage());
        }
    }

    private Long parseVendedorId(String idRaw) {
        if (idRaw == null || idRaw.trim().isEmpty() ||
                idRaw.equals("undefined") || idRaw.equals("null") || idRaw.equals("0")) {
            return null;
        }
        try {
            return Long.parseLong(idRaw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // =========================================================================
    // 3. DOSSIÊ EXECUTIVO 360º (GERAÇÃO AVANÇADA EM PDF)
    // =========================================================================
    @GetMapping("/dossie-executivo/pdf")
    public ResponseEntity<byte[]> baixarDossieExecutivoPdf(HttpServletRequest request) {
        try {
            String inicioRaw = request.getParameter("inicio");
            String fimRaw = request.getParameter("fim");

            LocalDate inicio = (inicioRaw != null && !inicioRaw.isEmpty()) ? LocalDate.parse(inicioRaw) : LocalDate.now().withDayOfMonth(1);
            LocalDate fim = (fimRaw != null && !fimRaw.isEmpty()) ? LocalDate.parse(fimRaw) : LocalDate.now();

            // Chama o motor de IA e PDF no Service
            byte[] pdfBytes = relatorioService.gerarDossieExecutivoPdf(inicio, fim);

            if (pdfBytes == null || pdfBytes.length == 0) {
                return ResponseEntity.internalServerError().body(null);
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=Dossie_Executivo_DD.pdf")
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("Erro Crítico ao gerar Dossiê Executivo: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    // =========================================================================
    // 4. BALANÇO TRIMESTRAL PARA INVESTIDORES / DIRETORIA (EARNINGS RELEASE)
    // =========================================================================
    @GetMapping("/balanco-trimestral/pdf")
    public ResponseEntity<byte[]> baixarBalancoTrimestral(
            @RequestParam("ano") int ano,
            @RequestParam("trimestre") int trimestre) {
        try {
            byte[] pdfBytes = relatorioService.gerarBalancoTrimestralPdf(ano, trimestre);

            if (pdfBytes == null || pdfBytes.length == 0) {
                return ResponseEntity.internalServerError().body(null);
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=Balanco_DD_Cosmeticos_" + trimestre + "T" + ano + ".pdf")
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("Erro ao gerar Balanço Trimestral: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
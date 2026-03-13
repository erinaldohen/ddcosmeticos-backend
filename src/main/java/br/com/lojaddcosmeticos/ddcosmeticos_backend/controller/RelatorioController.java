package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioComissaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;

    /**
     * Busca o relatório de comissões.
     * Nota: Utiliza HttpServletRequest para evitar bloqueios de serialização de data do Spring 3.x
     */
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
            log.error("[COMISSOES] Falha ao processar relatório: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Erro ao processar relatório.");
        }
    }

    /**
     * Trata o ID do vendedor vindo do Frontend para evitar NumberFormatException
     */
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
    @GetMapping("/comissoes/pdf")
    public ResponseEntity<?> baixarPdfComissoes(HttpServletRequest request) {
        try {
            // 1. Buscamos os dados usando a mesma lógica do relatório de tela
            String dataInicioRaw = request.getParameter("dataInicio");
            String dataFimRaw = request.getParameter("dataFim");
            String vendedorIdRaw = request.getParameter("vendedorId");

            LocalDateTime inicio = LocalDate.parse(dataInicioRaw).atStartOfDay();
            LocalDateTime fim = LocalDate.parse(dataFimRaw).atTime(LocalTime.MAX);

            Long idVendedor = null;
            if (vendedorIdRaw != null && !vendedorIdRaw.trim().isEmpty() && !vendedorIdRaw.equals("undefined")) {
                idVendedor = Long.parseLong(vendedorIdRaw);
            }

            // 2. Geramos o DTO de dados
            RelatorioComissaoDTO dados = relatorioService.gerarRelatorioComissoes(inicio, fim, idVendedor);

            // 3. Geramos o PDF a partir desses dados
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
}
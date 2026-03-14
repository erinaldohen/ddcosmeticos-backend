package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.AuditoriaService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor // Substitui os múltiplos @Autowired, gerando construtores seguros
public class AuditoriaController {

    private final AuditoriaService auditoriaService;
    private final RelatorioService relatorioService;

    // =========================================================================
    // 1. BUSCA SINCRONIZADA DA TIMELINE (BLINDADA CONTRA ERROS DE DATA)
    // =========================================================================
    @GetMapping("/eventos")
    public ResponseEntity<Page<Auditoria>> listarEventos(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            Pageable pageable) {

        try {
            // Converte a string "YYYY-MM-DD" do React para LocalDateTime com horas exatas
            LocalDateTime dataInicio = (inicio != null && !inicio.isBlank())
                    ? LocalDate.parse(inicio).atStartOfDay()
                    : null;

            LocalDateTime dataFim = (fim != null && !fim.isBlank())
                    ? LocalDate.parse(fim).atTime(LocalTime.MAX)
                    : null;

            return ResponseEntity.ok(auditoriaService.buscarFiltrado(search, dataInicio, dataFim, pageable));

        } catch (Exception e) {
            log.error("Erro ao processar datas na Timeline de Auditoria: ", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // =========================================================================
    // 2. OBTENÇÃO DA LIXEIRA (BLINDADA)
    // =========================================================================
    @GetMapping("/lixeira")
    public ResponseEntity<?> obterLixeira(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {

        try {
            LocalDate dataInicio = (inicio != null && !inicio.isBlank()) ? LocalDate.parse(inicio) : null;
            LocalDate dataFim = (fim != null && !fim.isBlank()) ? LocalDate.parse(fim) : null;

            var itensExcluidos = auditoriaService.obterItensLixeira(search, dataInicio, dataFim);
            return ResponseEntity.ok(itensExcluidos);

        } catch (Exception e) {
            log.error("Erro ao buscar lixeira: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Erro ao processar lixeira: " + e.getMessage());
        }
    }

    // =========================================================================
    // 3. EXPORTAÇÃO DE PDF COM GESTÃO DE STREAM
    // =========================================================================
    @GetMapping("/relatorio/pdf")
    public ResponseEntity<byte[]> exportarRelatorio(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {

        try {
            // O Service processa o HTML/Thymeleaf e converte para PDF
            byte[] pdfBytes = relatorioService.gerarPdfAuditoria(search, inicio, fim);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);

            // Define o nome do arquivo para o navegador
            String filename = "Auditoria_DD_" + System.currentTimeMillis() + ".pdf";
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

            // Cache-Control para evitar que o navegador armazene versões antigas de relatórios
            headers.setCacheControl(CacheControl.noCache().getHeaderValue());

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Erro ao gerar PDF de Auditoria: ", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // =========================================================================
    // 4. HISTÓRICO INDIVIDUAL DO PRODUTO
    // =========================================================================
    @GetMapping("/produto/{id}")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistoricoProduto(@PathVariable Long id) {
        try {
            List<HistoricoProdutoDTO> historico = auditoriaService.buscarHistoricoDoProduto(id);
            return ResponseEntity.ok(historico);
        } catch (Exception e) {
            log.error("🚨 ERRO CRÍTICO AO BUSCAR AUDITORIA DO PRODUTO {} 🚨", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.AuditoriaService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
@Tag(name = "Auditoria (Envers) e Timeline", description = "Monitorização de ações de operadores e rastreamento contínuo de segurança")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;
    private final RelatorioService relatorioService;

    @GetMapping("/eventos")
    @Operation(summary = "Listar Eventos Globais do Sistema", description = "Extrai eventos ordenados com conversão segura de Fuso Horário.")
    public ResponseEntity<Page<Auditoria>> listarEventos(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            Pageable pageable) {
        try {
            LocalDateTime dataInicio = (inicio != null && !inicio.isBlank()) ? LocalDate.parse(inicio).atStartOfDay() : null;
            LocalDateTime dataFim = (fim != null && !fim.isBlank()) ? LocalDate.parse(fim).atTime(LocalTime.MAX) : null;
            return ResponseEntity.ok(auditoriaService.buscarFiltrado(search, dataInicio, dataFim, pageable));
        } catch (Exception e) {
            log.error("Erro ao processar datas na Timeline de Auditoria: ", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/lixeira")
    @Operation(summary = "Obter Ficheiros/Produtos em Lixeira (Reciclagem)")
    public ResponseEntity<?> obterLixeira(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        try {
            LocalDate dataInicio = (inicio != null && !inicio.isBlank()) ? LocalDate.parse(inicio) : null;
            LocalDate dataFim = (fim != null && !fim.isBlank()) ? LocalDate.parse(fim) : null;
            return ResponseEntity.ok(auditoriaService.obterItensLixeira(search, dataInicio, dataFim));
        } catch (Exception e) {
            log.error("Erro ao buscar lixeira: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Erro ao processar lixeira: " + e.getMessage());
        }
    }

    @GetMapping("/relatorio/pdf")
    @Operation(summary = "Baixar Dossiê Completo de Auditoria em PDF")
    public ResponseEntity<byte[]> exportarRelatorio(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        try {
            byte[] pdfBytes = relatorioService.gerarPdfAuditoria(search, inicio, fim);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename("Auditoria_DD_" + System.currentTimeMillis() + ".pdf").build());
            headers.setCacheControl(CacheControl.noCache().getHeaderValue());
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Erro ao gerar PDF de Auditoria: ", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/produto/{id}")
    @Operation(summary = "Timeline Isolada por Produto", description = "Rastreia quem e a que horas alterou o preço ou as características críticas do artigo.")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistoricoProduto(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(auditoriaService.buscarHistoricoDoProduto(id));
        } catch (Exception e) {
            log.error("🚨 ERRO CRÍTICO AO BUSCAR AUDITORIA DO PRODUTO {} 🚨", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    @Operation(summary = "Criar Registo de Log Manual", description = "Usado pelo Frontend para reportar acessos anómalos ou erros visuais.")
    public ResponseEntity<Void> registrarAuditoriaManual(@RequestBody Map<String, Object> payload) {
        try {
            String acao = (String) payload.getOrDefault("acao", "INFO");
            String usuario = (String) payload.getOrDefault("operador", "Sistema");
            String detalhes = (String) payload.getOrDefault("detalhes", "");
            auditoriaService.registrarAcao(acao, usuario, detalhes);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Erro ao salvar log de auditoria via API: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
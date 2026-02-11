package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.AuditoriaService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/auditoria")
public class AuditoriaController {

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private RelatorioService relatorioService;

    // BUSCA SINCRONIZADA COM FILTROS
    @GetMapping("/eventos")
    public ResponseEntity<Page<Auditoria>> listarEventos(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            Pageable pageable) {

        return ResponseEntity.ok(auditoriaService.buscarFiltrado(search, inicio, fim, pageable));
    }

    // EXPORTAÇÃO DE PDF COM GESTÃO DE STREAM
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

            // Cache-Control para evitar que o navegador armazene versões antigas de relatórios sensíveis
            headers.setCacheControl(CacheControl.noCache().getHeaderValue());

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.AuditoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "*")
public class AuditoriaController {

    @Autowired private AuditoriaService auditoriaService;
    @Autowired private AuditoriaRepository auditoriaRepository;

    @GetMapping("/eventos")
    public ResponseEntity<List<Auditoria>> listarEventos() {
        return ResponseEntity.ok(auditoriaRepository.findAllByOrderByDataHoraDesc());
    }

    @GetMapping("/produto/{id}")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistoricoProduto(@PathVariable Long id) {
        return ResponseEntity.ok(auditoriaService.buscarHistoricoDoProduto(id));
    }

    @GetMapping("/lixeira")
    public ResponseEntity<List<Produto>> listarLixeira() {
        return ResponseEntity.ok(auditoriaService.buscarLixeira());
    }

    @PostMapping("/restaurar/{id}")
    public ResponseEntity<Void> restaurarProduto(@PathVariable Long id) {
        auditoriaService.restaurarProduto(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/relatorio/pdf")
    public ResponseEntity<byte[]> baixarRelatorio() {
        byte[] pdf = auditoriaService.gerarRelatorioMensalPDF();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=auditoria_dd.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
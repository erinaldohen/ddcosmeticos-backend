package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
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
// REMOVIDO DAQUI: @PreAuthorize("hasRole('ADMIN')") -> Isso estava bloqueando tudo!
// REMOVIDO: @CrossOrigin -> O SecurityConfig já resolve isso.
public class AuditoriaController {

    @Autowired private AuditoriaService auditoriaService;

    // --- LIBERADO PARA O DASHBOARD (Qualquer um logado) ---
    @GetMapping("/eventos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AuditoriaRequestDTO>> listarUltimosEventos() {
        return ResponseEntity.ok(auditoriaService.listarUltimasAlteracoes(10));
    }

    // --- RESTRITO: HISTÓRICO DE PRODUTO (Apenas Admin) ---
    @GetMapping("/produto/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<HistoricoProdutoDTO>> buscarHistoricoProduto(@PathVariable Long id) {
        return ResponseEntity.ok(auditoriaService.buscarHistoricoDoProduto(id));
    }

    // --- RESTRITO: LIXEIRA (Apenas Admin) ---
    @GetMapping("/lixeira")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Produto>> listarLixeira() {
        return ResponseEntity.ok(auditoriaService.buscarLixeira());
    }

    // --- RESTRITO: RESTAURAR (Apenas Admin) ---
    @PostMapping("/restaurar/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restaurarProduto(@PathVariable Long id) {
        auditoriaService.restaurarProduto(id);
        return ResponseEntity.ok().build();
    }

    // --- RESTRITO: RELATÓRIO PDF (Apenas Admin) ---
    @GetMapping("/relatorio/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> baixarRelatorio() {
        byte[] pdf = auditoriaService.gerarRelatorioMensalPDF();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=auditoria_dd.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
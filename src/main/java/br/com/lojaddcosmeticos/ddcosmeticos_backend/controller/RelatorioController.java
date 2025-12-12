package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO; // Novo Import
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/relatorios")
public class RelatorioController {

    @Autowired
    private RelatorioService relatorioService;

    @GetMapping("/diario")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<RelatorioDiarioDTO> getRelatorioDiario() {
        RelatorioDiarioDTO relatorio = relatorioService.gerarRelatorioDoDia();
        return ResponseEntity.ok(relatorio);
    }

    /**
     * Endpoint para Curva ABC.
     * Retorna a lista de produtos classificados por importância financeira.
     */
    @GetMapping("/curva-abc")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<List<ItemAbcDTO>> getCurvaAbc() {
        List<ItemAbcDTO> relatorio = relatorioService.gerarCurvaAbc();
        return ResponseEntity.ok(relatorio);
    }
}
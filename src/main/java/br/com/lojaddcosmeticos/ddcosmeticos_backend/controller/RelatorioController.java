package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.InventarioResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO; // Novo Import
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioPerdasDTO;
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

    @Autowired
    private br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository auditoriaRepository;

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

    @GetMapping("/inventario/contabil")
    public ResponseEntity<InventarioResponseDTO> getInventarioContabil() {
        // Retorna apenas produtos com NF (Para o contador)
        return ResponseEntity.ok(relatorioService.gerarInventarioEstoque(true));
    }

    @GetMapping("/inventario/gerencial")
    public ResponseEntity<InventarioResponseDTO> getInventarioGerencial() {
        // Retorna TUDO (Para controle de perdas e compras)
        return ResponseEntity.ok(relatorioService.gerarInventarioEstoque(false));
    }

    // Relatório de Perdas e Ajustes (O "Livro Negro" do estoque)
    @GetMapping("/auditoria/ajustes-estoque")
    public ResponseEntity<List<br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria>> getHistoricoAjustes() {
        // Exemplo simples: busca tudo. Em produção, poderia ter filtro de data.
        // O ideal é criar um método no Repository: findByTipoEvento("INVENTARIO_ESTOQUE")

        // Estamos usando findAll com stream para filtrar rápido, mas o ideal é no banco
        List<br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria> ajustes = auditoriaRepository.findAll().stream()
                .filter(a -> "INVENTARIO_ESTOQUE".equals(a.getTipoEvento()))
                .toList();

        return ResponseEntity.ok(ajustes);
    }

    @GetMapping("/perdas/motivos")
    public ResponseEntity<List<RelatorioPerdasDTO>> getPerdasPorMotivo() {
        return ResponseEntity.ok(relatorioService.gerarRelatorioPerdasPorMotivo());
    }
}
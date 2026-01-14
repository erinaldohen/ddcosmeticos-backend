package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoria", description = "Logs de sistema, eventos do PDV e histórico de alterações")
public class AuditoriaController {

    @Autowired
    private AuditoriaService auditoriaService;

    // --- CORREÇÃO: Recebe DTO e chama o método blindado do Service ---
    @PostMapping
    @Operation(summary = "Registrar auditoria manual (Logs do Frontend/PDV)")
    public ResponseEntity<Void> registrarAuditoriaManual(@RequestBody AuditoriaRequestDTO dto) {
        // Passa os dados para o serviço que trata o usuário logado e mapeia para a entidade
        auditoriaService.registrar(dto.acao(), dto.detalhes());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/timeline/{id}")
    @Operation(summary = "Ver histórico de alterações de um produto (Envers)")
    public ResponseEntity<List<HistoricoProdutoDTO>> getHistorico(@PathVariable Long id) {
        List<HistoricoProdutoDTO> historico = auditoriaService.buscarHistoricoDoProduto(id);
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/lixeira")
    @Operation(summary = "Listar produtos excluídos")
    public ResponseEntity<List<Produto>> getLixeira() {
        List<Produto> lixeira = auditoriaService.buscarLixeira();
        if (lixeira.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(lixeira);
    }

    @PutMapping("/restaurar/{id}")
    @Operation(summary = "Restaurar produto da lixeira")
    public ResponseEntity<String> restaurar(@PathVariable Long id) {
        auditoriaService.restaurarProduto(id);
        return ResponseEntity.ok("Produto restaurado com sucesso!");
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro.ContaReceberDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ContaReceberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contas-receber")
@Tag(name = "Contas a Receber", description = "Monitoramento de Créditos, Inadimplência e Liquidação Financeira")
@RequiredArgsConstructor
public class ContaReceberController {

    private final ContaReceberService service;

    @GetMapping
    @Operation(summary = "Lista todas as Contas a Receber", description = "Filtro de busca livre e status de pagamento.")
    public ResponseEntity<List<ContaReceberDTO>> listar(
            @RequestParam(required = false, defaultValue = "TODAS") String status,
            @RequestParam(required = false) String termo) {
        return ResponseEntity.ok(service.listar(status, termo));
    }

    @GetMapping("/resumo")
    @Operation(summary = "Dashboard de Saldos (Contas a Receber)")
    public ResponseEntity<ContaReceberDTO.ResumoContasDTO> obterResumo() {
        return ResponseEntity.ok(service.obterResumo());
    }

    @PostMapping("/{id}/baixar")
    @Operation(summary = "Registrar entrada de Capital (Baixa de Título)")
    public ResponseEntity<Void> baixarTitulo(
            @PathVariable Long id,
            @RequestBody ContaReceberDTO.BaixaTituloDTO dto) {
        service.baixarTitulo(id, dto);
        return ResponseEntity.ok().build();
    }
}
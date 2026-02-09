package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro.ContaReceberDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ContaReceberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contas-receber")
public class ContaReceberController {

    @Autowired
    private ContaReceberService service;

    @GetMapping
    public ResponseEntity<List<ContaReceberDTO>> listar(
            @RequestParam(required = false, defaultValue = "TODAS") String status,
            @RequestParam(required = false) String termo) {
        return ResponseEntity.ok(service.listar(status, termo));
    }

    @GetMapping("/resumo")
    public ResponseEntity<ContaReceberDTO.ResumoContasDTO> obterResumo() {
        return ResponseEntity.ok(service.obterResumo());
    }

    @PostMapping("/{id}/baixar")
    public ResponseEntity<Void> baixarTitulo(
            @PathVariable Long id,
            @RequestBody ContaReceberDTO.BaixaTituloDTO dto) {
        service.baixarTitulo(id, dto);
        return ResponseEntity.ok().build();
    }
}
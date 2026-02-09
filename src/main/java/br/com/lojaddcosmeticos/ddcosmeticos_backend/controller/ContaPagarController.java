package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro.ContaPagarDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ContaPagarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contas-pagar")
public class ContaPagarController {

    @Autowired
    private ContaPagarService service;

    @GetMapping
    public ResponseEntity<List<ContaPagarDTO>> listar(
            @RequestParam(required = false, defaultValue = "TODAS") String status,
            @RequestParam(required = false) String termo) {
        return ResponseEntity.ok(service.listar(status, termo));
    }

    @GetMapping("/resumo")
    public ResponseEntity<ContaPagarDTO.ResumoPagarDTO> obterResumo() {
        return ResponseEntity.ok(service.obterResumo());
    }

    @PostMapping
    public ResponseEntity<ContaPagarDTO> criar(@RequestBody ContaPagarDTO.NovaContaDTO dto) {
        return ResponseEntity.ok(service.criar(dto));
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<Void> pagarConta(
            @PathVariable Long id,
            @RequestBody ContaPagarDTO.BaixaContaPagarDTO dto) {
        service.pagarConta(id, dto);
        return ResponseEntity.ok().build();
    }
}
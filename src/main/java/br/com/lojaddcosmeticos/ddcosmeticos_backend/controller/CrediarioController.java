package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DevedorResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FaturaClienteDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RecebimentoRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CrediarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/financeiro/crediario")
public class CrediarioController {

    @Autowired
    private CrediarioService service;

    @GetMapping("/resumo")
    public ResponseEntity<List<DevedorResumoDTO>> listarResumoDevedores() {
        return ResponseEntity.ok(service.listarResumoDevedores());
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<FaturaClienteDTO>> listarFaturasAbertas(@PathVariable Long idCliente) {
        return ResponseEntity.ok(service.listarFaturasAbertasDoCliente(idCliente));
    }

    @PostMapping("/receber/{idFatura}")
    public ResponseEntity<Void> receberPagamento(
            @PathVariable Long idFatura,
            @RequestBody RecebimentoRequestDTO request) {

        service.processarRecebimento(idFatura, request);
        return ResponseEntity.ok().build();
    }
}
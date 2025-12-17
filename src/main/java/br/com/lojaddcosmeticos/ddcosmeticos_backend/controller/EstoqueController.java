package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO; // Importe o novo DTO
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/estoque")
public class EstoqueController {

    @Autowired
    private EstoqueService estoqueService;

    // Endpoint de Compra (Entrada normal com Nota)
    @PostMapping("/entrada")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<String> registrarEntrada(@RequestBody @Valid EstoqueRequestDTO dados) {
        estoqueService.registrarEntrada(dados);
        return ResponseEntity.ok("Entrada de mercadoria registrada com sucesso. PMP atualizado.");
    }

    // Endpoint de Inventário (Ajuste Manual)
    @PostMapping("/ajuste")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<String> ajustarEstoque(@RequestBody @Valid AjusteEstoqueDTO dados) {
        estoqueService.realizarAjusteInventario(dados);
        return ResponseEntity.ok("Estoque ajustado com sucesso e auditoria registrada.");
    }


}
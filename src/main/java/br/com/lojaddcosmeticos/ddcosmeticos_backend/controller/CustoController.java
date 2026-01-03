package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CustoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/custos")
@Tag(name = "Custos", description = "Gerenciamento de custos e precificação")
public class CustoController {

    @Autowired
    private CustoService custoService;

    // INJEÇÃO DO ESTOQUE SERVICE (Quem realmente processa a entrada da NF)
    @Autowired
    private EstoqueService estoqueService;

    @PostMapping("/entrada-nf")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Registrar Entrada via Nota Fiscal", description = "Atualiza o estoque e recalcula o Preço Médio Ponderado (PMP).")
    public ResponseEntity<Void> registrarEntradaNF(@RequestBody @Valid EstoqueRequestDTO dto) {

        // CORREÇÃO: O método registrarEntrada fica no EstoqueService, pois envolve movimentação física.
        // O EstoqueService internamente chama o CustoService para atualizar o PMP.
        estoqueService.registrarEntrada(dto);

        return ResponseEntity.ok().build();
    }

}
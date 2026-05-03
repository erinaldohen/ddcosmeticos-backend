package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
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
@Tag(name = "Custos e Valoração", description = "Gestão financeira de entrada de mercadorias (Preço Médio Ponderado)")
public class CustoController {

    @Autowired
    private EstoqueService estoqueService;

    @PostMapping("/entrada-nf")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Registrar Entrada via Nota Fiscal", description = "Atualiza o estoque físico e recalcula automaticamente o Preço Médio Ponderado (PMP) e os custos.")
    public ResponseEntity<Void> registrarEntradaNF(@RequestBody @Valid EstoqueRequestDTO dto) {
        estoqueService.registrarEntrada(dto);
        return ResponseEntity.ok().build();
    }
}
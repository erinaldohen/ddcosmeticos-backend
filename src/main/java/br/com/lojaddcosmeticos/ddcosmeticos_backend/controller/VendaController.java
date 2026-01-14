package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaCompletaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/vendas")
@Tag(name = "Vendas", description = "Gestão de Vendas, Orçamentos e Fila de Espera")
public class VendaController {

    @Autowired
    private VendaService vendaService;

    // ==================================================================================
    // SESSÃO 1: OPERAÇÕES PRINCIPAIS
    // ==================================================================================

    @PostMapping
    @Operation(summary = "Realizar Venda (PDV)")
    public ResponseEntity<VendaResponseDTO> realizarVenda(@RequestBody @Valid VendaRequestDTO dto) {
        // O serviço já retorna o DTO pronto com os cálculos fiscais.
        VendaResponseDTO response = vendaService.realizarVenda(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/suspender")
    @Operation(summary = "Suspender Venda (Fila de Espera)")
    public ResponseEntity<VendaCompletaResponseDTO> suspenderVenda(@RequestBody @Valid VendaRequestDTO dto) {
        Venda venda = vendaService.suspenderVenda(dto);
        return ResponseEntity.ok(converterParaDTO(venda));
    }

    @PostMapping("/{id}/efetivar")
    @Operation(summary = "Efetivar Venda Suspensa/Orçamento")
    public ResponseEntity<VendaCompletaResponseDTO> efetivarVenda(@PathVariable Long id) {
        Venda venda = vendaService.efetivarVenda(id);
        return ResponseEntity.ok(converterParaDTO(venda));
    }

    // ==================================================================================
    // SESSÃO 2: CONSULTAS
    // ==================================================================================

    @GetMapping("/suspensas")
    @Operation(summary = "Listar Vendas Suspensas")
    public ResponseEntity<List<VendaResponseDTO>> listarSuspensas() {
        return ResponseEntity.ok(vendaService.listarVendasSuspensas());
    }

    @GetMapping
    public ResponseEntity<Page<VendaResponseDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @PageableDefault(size = 20, sort = "dataVenda") Pageable pageable) {
        return ResponseEntity.ok(vendaService.listarVendas(inicio, fim, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendaCompletaResponseDTO> buscarPorId(@PathVariable Long id) {
        Venda venda = vendaService.buscarVendaComItens(id);
        return ResponseEntity.ok(converterParaDTO(venda));
    }

    // ==================================================================================
    // SESSÃO 3: CANCELAMENTO
    // ==================================================================================

    @PutMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    public ResponseEntity<Void> cancelarVenda(@PathVariable Long id, @RequestBody String motivo) {
        vendaService.cancelarVenda(id, motivo);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================================
    // SESSÃO 4: AUXILIARES
    // ==================================================================================

    private VendaCompletaResponseDTO converterParaDTO(Venda venda) {
        // CORREÇÃO LINHA 102: Conversão segura de Enum para String
        String status = (venda.getStatusNfce() != null) ? venda.getStatusNfce().name() : "N/A";

        return new VendaCompletaResponseDTO(
                venda.getIdVenda(),
                venda.getDataVenda(),
                venda.getClienteNome(),
                venda.getClienteDocumento(),
                venda.getValorTotal(),
                venda.getDescontoTotal(),
                status,
                venda.getItens().stream()
                        .map(i -> i.getProduto().getDescricao())
                        .collect(Collectors.toList())
        );
    }
}
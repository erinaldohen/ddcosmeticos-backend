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
import org.springframework.http.HttpStatus;
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

    @PostMapping("/suspender")
    @Operation(summary = "Suspender Venda (Fila de Espera)", description = "Salva os itens para atender outro cliente. Não movimenta financeiro nem estoque.")
    public ResponseEntity<VendaCompletaResponseDTO> suspenderVenda(@RequestBody @Valid VendaRequestDTO dto) {
        Venda venda = vendaService.suspenderVenda(dto);
        return ResponseEntity.ok(converterParaDTO(venda));
    }

    @PostMapping("/{id}/efetivar")
    @Operation(summary = "Efetivar Venda Suspensa/Orçamento", description = "Retoma uma venda suspensa ou orçamento e finaliza (baixa estoque/gera financeiro).")
    public ResponseEntity<VendaCompletaResponseDTO> efetivarVenda(@PathVariable Long id) {
        Venda venda = vendaService.efetivarVenda(id);
        return ResponseEntity.ok(converterParaDTO(venda));
    }

    // ==================================================================================
    // SESSÃO 2: CONSULTAS
    // ==================================================================================

    @GetMapping("/suspensas")
    @Operation(summary = "Listar Vendas Suspensas", description = "Mostra a fila de espera (clientes aguardando).")
    public ResponseEntity<List<VendaResponseDTO>> listarSuspensas() {
        List<Venda> vendasSuspensas = vendaService.listarVendasSuspensas();

        // --- CORREÇÃO AQUI ---
        // Substituímos 'new VendaResponseDTO(venda)' pelo método estático 'fromEntity'
        List<VendaResponseDTO> response = vendasSuspensas.stream()
                .map(VendaResponseDTO::fromEntity)
                .collect(Collectors.toList());
        // ---------------------

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<VendaResponseDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @PageableDefault(size = 20, sort = "dataVenda") Pageable pageable) {

        // Dica: O seu Service também estava montando o DTO manualmente.
        // O ideal é que ele também use o VendaResponseDTO::fromEntity,
        // mas como o erro de compilação estava aqui no controller, foquei a correção aqui.
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

    /**
     * Este método converte para VendaCompletaResponseDTO (Detalhado).
     * O erro anterior era no VendaResponseDTO (Resumido/Lista).
     */
    private VendaCompletaResponseDTO converterParaDTO(Venda venda) {
        return new VendaCompletaResponseDTO(
                venda.getId(),
                venda.getDataVenda(),
                venda.getClienteNome(),
                venda.getClienteDocumento(),
                venda.getTotalVenda(),
                venda.getDescontoTotal(),
                venda.getStatusFiscal().name(),
                venda.getItens().stream()
                        .map(i -> i.getProduto().getDescricao())
                        .collect(Collectors.toList())
        );
    }

    @PostMapping
    @Operation(summary = "Realizar Venda", description = "Finaliza uma venda baixando estoque e gerando financeiro.")
    public ResponseEntity<VendaResponseDTO> realizarVenda(@RequestBody @Valid VendaRequestDTO dto) {
        Venda venda = vendaService.realizarVenda(dto);
        // CORREÇÃO LINHA 125: Usando o método estático fromEntity em vez de 'new'
        return ResponseEntity.ok(VendaResponseDTO.fromEntity(venda));
    }
}
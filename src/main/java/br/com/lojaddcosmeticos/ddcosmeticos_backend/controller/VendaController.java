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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/vendas")
@Tag(name = "Vendas", description = "Operações do PDV e histórico de vendas")
public class VendaController {

    @Autowired private VendaService vendaService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    @Operation(summary = "Registrar Venda", description = "Processa o carrinho e finaliza a venda.")
    public ResponseEntity<VendaResponseDTO> realizarVenda(@RequestBody @Valid VendaRequestDTO dto) {
        Venda venda = vendaService.realizarVenda(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                VendaResponseDTO.builder()
                        .idVenda(venda.getId())
                        .dataVenda(venda.getDataVenda())
                        .valorTotal(venda.getTotalVenda())
                        .desconto(venda.getDescontoTotal())
                        .totalItens(venda.getItens().size())
                        .statusFiscal(venda.getStatusFiscal())
                        .alertas(new ArrayList<>())
                        .build()
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    @Operation(summary = "Listar Histórico", description = "Busca vendas com filtro de data e paginação.")
    public ResponseEntity<Page<VendaResponseDTO>> listarHistorico(
            @RequestParam(required = false) LocalDate inicio,
            @RequestParam(required = false) LocalDate fim,
            @PageableDefault(size = 20, sort = "dataVenda", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(vendaService.listarVendas(inicio, fim, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    @Operation(summary = "Detalhes da Venda", description = "Busca todos os itens de uma venda específica.")
    public ResponseEntity<VendaCompletaResponseDTO> buscarDetalhes(@PathVariable Long id) {
        Venda venda = vendaService.buscarVendaComItens(id);
        return ResponseEntity.ok(new VendaCompletaResponseDTO(venda));
    }

    @PutMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('GERENTE')") // Segurança reforçada: Apenas Gerente
    @Operation(summary = "Cancelar Venda", description = "Estorna estoque e financeiro.")
    public ResponseEntity<Void> cancelarVenda(@PathVariable Long id, @RequestBody(required = false) String motivo) {
        String motivoFinal = (motivo != null && !motivo.isBlank()) ? motivo : "Cancelamento solicitado pelo gerente";
        vendaService.cancelarVenda(id, motivoFinal);
        return ResponseEntity.noContent().build();
    }
}
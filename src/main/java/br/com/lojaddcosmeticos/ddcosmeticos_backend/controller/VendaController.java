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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/vendas")
@Tag(name = "Vendas", description = "Operações do PDV e histórico de vendas")
public class VendaController {

    @Autowired
    private VendaService vendaService;

    /**
     * ÚNICO PONTO DE ENTRADA PARA VENDAS.
     * Resolve o erro de Ambiguous Mapping e unifica auditoria + resposta.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    @Operation(summary = "Registrar Venda", description = "Processa o carrinho, baixa estoque, gera financeiro e registra o vendedor.")
    public ResponseEntity<VendaResponseDTO> realizarVenda(@RequestBody @Valid VendaRequestDTO dto, Principal principal) {

        // 1. Auditoria: Captura o usuário logado via Principal
        String usuarioLogado = (principal != null) ? principal.getName() : "SISTEMA_LOCAL";

        // 2. Orquestração: Chama o método finalizarVenda que já corrigimos no Service
        Venda venda = vendaService.realizarVenda(dto);

        // 3. Resposta: Mapeia para o DTO de performance (ResponseDTO)
        VendaResponseDTO response = VendaResponseDTO.builder()
                .idVenda(venda.getId())
                .dataVenda(venda.getDataVenda())
                .valorTotal(venda.getTotalVenda())
                .desconto(venda.getDescontoTotal())
                .totalItens(venda.getItens().size())
                .statusFiscal(venda.getStatusFiscal())
                .alertas(new ArrayList<>())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Busca os detalhes completos de uma venda.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    @Operation(summary = "Detalhes da Venda", description = "Busca todos os itens e totais de uma venda realizada.")
    public ResponseEntity<VendaCompletaResponseDTO> buscarDetalhes(@PathVariable Long id) {
        Venda venda = vendaService.buscarVendaComItens(id);
        return ResponseEntity.ok(new VendaCompletaResponseDTO(venda));
    }
}
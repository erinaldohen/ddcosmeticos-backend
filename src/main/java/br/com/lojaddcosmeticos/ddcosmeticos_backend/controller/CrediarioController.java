package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DevedorResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FaturaClienteDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RecebimentoRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CrediarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/financeiro/crediario")
@Tag(name = "Crediário Próprio", description = "Operações Financeiras de Vendas Fiado/A Prazo e Cobrança de Clientes")
@RequiredArgsConstructor
public class CrediarioController {

    private final CrediarioService service;

    @GetMapping("/resumo")
    @Operation(summary = "Listagem de Devedores (Inadimplência Global)", description = "Agrupa todos os clientes com saldo devedor na loja e totaliza a dívida.")
    public ResponseEntity<List<DevedorResumoDTO>> listarResumoDevedores() {
        return ResponseEntity.ok(service.listarResumoDevedores());
    }

    @GetMapping("/cliente/{idCliente}")
    @Operation(summary = "Faturas em Aberto por Cliente", description = "Detalha todas as vendas fiado que compõem a dívida do cliente especificado.")
    public ResponseEntity<List<FaturaClienteDTO>> listarFaturasAbertas(@PathVariable Long idCliente) {
        return ResponseEntity.ok(service.listarFaturasAbertasDoCliente(idCliente));
    }

    @PostMapping("/receber/{idFatura}")
    @Operation(summary = "Liquidar Fatura (Baixar Fiado)", description = "Abate o valor pago da fatura do cliente e injeta o capital no Caixa Diário.")
    public ResponseEntity<Void> receberPagamento(
            @PathVariable Long idFatura,
            @RequestBody RecebimentoRequestDTO request) {

        service.processarRecebimento(idFatura, request);
        return ResponseEntity.ok().build();
    }
}
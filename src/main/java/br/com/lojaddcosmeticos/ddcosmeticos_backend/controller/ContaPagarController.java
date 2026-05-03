package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro.ContaPagarDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ContaPagarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/contas-pagar")
@Tag(name = "Contas a Pagar", description = "Gestão de Despesas, Fornecedores e Custos Fixos")
@RequiredArgsConstructor
public class ContaPagarController {

    private final ContaPagarService service;

    @GetMapping
    @Operation(summary = "Lista todas as Contas a Pagar", description = "Suporta filtros de status e termo de busca (Fornecedor/Descrição).")
    public ResponseEntity<List<ContaPagarDTO>> listar(
            @RequestParam(required = false, defaultValue = "TODAS") String status,
            @RequestParam(required = false) String termo) {
        return ResponseEntity.ok(service.listar(status, termo));
    }

    @GetMapping("/resumo")
    @Operation(summary = "Dashboard de Saldos (Contas a Pagar)")
    public ResponseEntity<ContaPagarDTO.ResumoPagarDTO> obterResumo() {
        return ResponseEntity.ok(service.obterResumo());
    }

    @GetMapping("/analise-mensal")
    @Operation(summary = "Análise inteligente de custos do mês (Fixos, Variáveis e Mercadorias)")
    public ResponseEntity<Map<String, Object>> obterAnaliseMensal(
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano) {

        int m = mes != null ? mes : LocalDate.now().getMonthValue();
        int a = ano != null ? ano : LocalDate.now().getYear();

        return ResponseEntity.ok(service.obterAnaliseInteligenteDoMes(m, a));
    }

    @PostMapping
    @Operation(summary = "Criar nova Despesa/Conta a Pagar manualmente")
    public ResponseEntity<ContaPagarDTO> criar(@RequestBody ContaPagarDTO.NovaContaDTO dto) {
        return ResponseEntity.ok(service.criar(dto));
    }

    @PostMapping("/{id}/pagar")
    @Operation(summary = "Efetuar o pagamento (Baixa) de uma Conta")
    public ResponseEntity<Void> pagarConta(
            @PathVariable Long id,
            @RequestBody ContaPagarDTO.BaixaContaPagarDTO dto) {
        service.pagarConta(id, dto);
        return ResponseEntity.ok().build();
    }
}
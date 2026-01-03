package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FinanceiroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/caixa")
@Tag(name = "Caixa", description = "Operações de conferência e fechamento diário")
public class CaixaController {

    @Autowired
    private FinanceiroService financeiroService;

    @GetMapping("/fechamento")
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    @Operation(summary = "Gerar Fechamento de Caixa", description = "Resumo detalhado para conferência de valores, incluindo sangrias e suprimentos.")
    public ResponseEntity<FechamentoCaixaDTO> obterFechamento(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {

        return ResponseEntity.ok(financeiroService.gerarResumoFechamento(data));
    }
}
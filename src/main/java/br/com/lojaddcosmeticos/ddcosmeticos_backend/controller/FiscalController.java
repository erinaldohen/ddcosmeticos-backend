package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ValidacaoFiscalDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FiscalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/fiscal")
@Tag(name = "Motor Fiscal NCM", description = "Validação Dinâmica de Classificação Tributária para o Front-end")
public class FiscalController {

    @Autowired
    private FiscalService fiscalService;

    @PostMapping("/validar")
    @Operation(summary = "Validar NCM e Monofásico on-the-fly", description = "Usado durante a digitação no Front-end para evitar NCMs proibidos no retalho de cosméticos.")
    public ResponseEntity<ValidacaoFiscalDTO> validarDadosFiscais(@RequestBody Map<String, String> payload) {
        String descricao = payload.get("descricao");
        String ncm = payload.get("ncm");

        return ResponseEntity.ok(fiscalService.validarProduto(descricao, ncm));
    }
}
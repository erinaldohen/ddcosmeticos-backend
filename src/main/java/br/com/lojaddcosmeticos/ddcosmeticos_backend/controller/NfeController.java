package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fiscal/nfe")
@Tag(name = "Fiscal NF-e (Modelo 55)", description = "Emissão de Notas para Atacado/Empresas")
public class NfeController {

    @Autowired
    private NfeService nfeService;

    @PostMapping("/emitir/{idVenda}")
    @Operation(summary = "Emitir Nota Fiscal Eletrônica (Modelo 55)", description = "Gera e autoriza uma NF-e para a venda especificada. Exige cliente completo.")
    public ResponseEntity<NfceResponseDTO> emitirNfe(@PathVariable Long idVenda) {
        return ResponseEntity.ok(nfeService.emitirNfeModelo55(idVenda));
    }
}
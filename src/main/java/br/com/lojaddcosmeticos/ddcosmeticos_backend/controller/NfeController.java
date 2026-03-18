package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/fiscal/nfe")
@Tag(name = "Fiscal NF-e (Modelo 55)", description = "Emissão de Notas para Atacado/Empresas e Status Sefaz")
public class NfeController {

    @Autowired
    private NfeService nfeService;

    @GetMapping("/status")
    @Operation(summary = "Verificar Status SEFAZ NF-e", description = "Testa a comunicação com a SEFAZ para Notas Maiores (Modelo 55).")
    public ResponseEntity<String> verificarStatusSefaz() {
        try {
            log.info("Iniciando ping no servidor da SEFAZ (NF-e)...");
            String status = nfeService.consultarStatusSefaz();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Falha de comunicação SEFAZ NF-e: ", e);
            return ResponseEntity.internalServerError().body("Erro ao consultar SEFAZ NF-e: " + e.getMessage());
        }
    }

    @PostMapping("/emitir/{idVenda}")
    @Operation(summary = "Emitir NF-e", description = "Gera, assina e transmite uma NF-e modelo 55 para o Governo.")
    public ResponseEntity<?> emitirNfe(@PathVariable Long idVenda) {
        log.info("Recebida solicitação de emissão NF-e (Modelo 55) para venda ID: {}", idVenda);
        try {
            NfceResponseDTO retorno = nfeService.emitirNfeModelo55(idVenda);
            return ResponseEntity.ok(retorno);
        } catch (Exception e) {
            log.error("Erro na emissão da NF-e: ", e);
            return ResponseEntity.badRequest().body("Erro ao emitir: " + e.getMessage());
        }
    }
}
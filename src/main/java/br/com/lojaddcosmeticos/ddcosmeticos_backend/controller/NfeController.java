package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ImpressaoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/fiscal/nfe")
@Tag(name = "Fiscal NF-e (Modelo 55)", description = "Emissão de Notas para Atacado, Devoluções e Fornecedores")
public class NfeController {

    @Autowired private NfeService nfeService;
    @Autowired private ImpressaoService impressaoService;

    @GetMapping("/status")
    @Operation(summary = "Verificar Status SEFAZ NF-e", description = "Testa a comunicação com a SEFAZ para Notas Maiores (Modelo 55).")
    public ResponseEntity<String> verificarStatusSefaz() {
        try {
            log.info("A iniciar ping ao servidor da SEFAZ (NF-e)...");
            return ResponseEntity.ok(nfeService.consultarStatusSefaz());
        } catch (Exception e) {
            log.error("Falha de comunicação SEFAZ NF-e: ", e);
            return ResponseEntity.internalServerError().body("Erro ao consultar SEFAZ NF-e: " + e.getMessage());
        }
    }

    @PostMapping("/emitir/{idVenda}")
    @Operation(summary = "Emitir NF-e", description = "Gera, assina e transmite uma NF-e modelo 55 para a SEFAZ.")
    public ResponseEntity<?> emitirNfe(@PathVariable Long idVenda) {
        log.info("Solicitação de emissão NF-e (Modelo 55) recebida para Venda ID: {}", idVenda);
        try {
            NfceResponseDTO retorno = nfeService.emitirNfeModelo55(idVenda);
            return ResponseEntity.ok(retorno);
        } catch (Exception e) {
            log.error("Erro na emissão da NF-e: ", e);
            return ResponseEntity.badRequest().body("Erro ao emitir: " + e.getMessage());
        }
    }

    @GetMapping(value = "/{idVenda}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Visualizar DANFE A4 (PDF)", description = "Gera o PDF da DANFE A4 pronto para impressão (Inline viewer).")
    public ResponseEntity<byte[]> baixarDanfePdf(@PathVariable Long idVenda) {
        byte[] pdfBytes = impressaoService.gerarDanfeA4(idVenda);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=DANFE_" + idVenda + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
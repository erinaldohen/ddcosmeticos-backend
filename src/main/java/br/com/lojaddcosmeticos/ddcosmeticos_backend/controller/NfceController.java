package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ImpressaoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfceService;
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
@RequestMapping("/api/v1/fiscal/nfce")
@Tag(name = "Fiscal NFC-e", description = "Emissão, Impressão e Status de Cupom Fiscal (Modelo 65)")
public class NfceController {

    @Autowired
    private NfceService nfceService;

    @Autowired
    private ImpressaoService impressaoService;

    // ==================================================================================
    // STATUS SEFAZ (PING NFC-e - MODELO 65)
    // ==================================================================================
    @GetMapping("/status")
    @Operation(summary = "Verificar Status SEFAZ NFC-e", description = "Testa a comunicação com os servidores da SEFAZ para emissão de Cupons Fiscais.")
    public ResponseEntity<String> verificarStatusSefazNfce() {
        try {
            log.info("Iniciando ping no servidor da SEFAZ (NFC-e)...");
            String status = nfceService.consultarStatusSefaz();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Falha de comunicação SEFAZ NFC-e: ", e);
            return ResponseEntity.internalServerError().body("Erro ao consultar SEFAZ NFC-e: " + e.getMessage());
        }
    }

    // ==================================================================================
    // IMPRESSÃO DE CUPOM (PDF)
    // ==================================================================================
    @GetMapping(value = "/imprimir/{idVenda}", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Imprimir Cupom (PDF)", description = "Gera o PDF do DANFE NFC-e pronto para impressão térmica.")
    public ResponseEntity<byte[]> imprimirCupom(@PathVariable Long idVenda) {
        byte[] pdf = impressaoService.gerarCupomNfce(idVenda);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=cupom_" + idVenda + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
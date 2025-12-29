package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ImpressaoService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fiscal/nfce")
@Tag(name = "Fiscal NFC-e", description = "Emissão e Impressão de Cupom Fiscal")
public class NfceController {

    @Autowired
    private NfceService nfceService;

    @Autowired
    private ImpressaoService impressaoService; // Injeção do novo serviço

    // (O endpoint de emissão existente pode estar aqui, omitido para focar no novo)

    // ==================================================================================
    // SESSÃO: IMPRESSÃO DE CUPOM (ENDPOINT NOVO)
    // ==================================================================================
    @GetMapping(value = "/imprimir/{idVenda}", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Imprimir Cupom (PDF)", description = "Gera o PDF do DANFE NFC-e pronto para impressão térmica.")
    public ResponseEntity<byte[]> imprimirCupom(@PathVariable Long idVenda) {

        byte[] pdf = impressaoService.gerarCupomNfce(idVenda);

        return ResponseEntity.ok()
                // Define o header para o navegador entender que é um PDF para download/visualização
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=cupom_" + idVenda + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
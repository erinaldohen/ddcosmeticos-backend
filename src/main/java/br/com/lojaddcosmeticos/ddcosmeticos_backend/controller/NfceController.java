package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
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

import java.util.Optional;

@Slf4j
@RestController
// 🚨 CORREÇÃO: Mapeamento duplo para garantir que o frontend encontre a rota com ou sem o "/fiscal"
@RequestMapping({"/api/v1/fiscal/nfce", "/api/v1/nfce"})
@Tag(name = "Fiscal NFC-e", description = "Emissão, Impressão e Status de Cupom Fiscal (Modelo 65)")
public class NfceController {

    @Autowired
    private NfceService nfceService;

    @Autowired
    private ImpressaoService impressaoService;

    @Autowired
    private VendaRepository vendaRepository; // Injetado para podermos buscar vendas falhadas

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

    // ==================================================================================
    // RETRANSMISSÃO MANUAL DE NFC-E (CONTINGÊNCIA / REJEIÇÃO)
    // ==================================================================================
    @PostMapping("/retransmitir/{idVenda}")
    @Operation(summary = "Retransmitir NFC-e", description = "Tenta reemitir uma nota fiscal que ficou em contingência ou foi rejeitada pela SEFAZ.")
    public ResponseEntity<?> retransmitirNfce(@PathVariable Long idVenda) {
        try {
            Optional<Venda> vendaOpt = vendaRepository.findById(idVenda);
            if (vendaOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Venda venda = vendaOpt.get();

            // Tenta processar a emissão novamente
            NfceResponseDTO resposta = nfceService.emitirNfce(venda);

            return ResponseEntity.ok(resposta);

        } catch (Exception e) {
            log.error("Erro ao retransmitir NFC-e da venda {}: {}", idVenda, e.getMessage());
            return ResponseEntity.badRequest().body("Falha na retransmissão: " + e.getMessage());
        }
    }

    // =====================================================================
    // SERVE O PDF DO CUPOM TÉRMICO (80mm) PARA O NAVEGADOR
    // =====================================================================
    @GetMapping(value = "/{idVenda}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Baixar Cupom (PDF via Frontend)", description = "Rota otimizada para o frontend baixar ou abrir o PDF em nova aba.")
    public ResponseEntity<byte[]> baixarCupomPdf(@PathVariable Long idVenda) {
        byte[] pdfBytes = impressaoService.gerarCupomNfce(idVenda);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        // "inline" faz o PDF abrir no navegador em vez de baixar automaticamente
        headers.setContentDispositionFormData("inline", "CupomFiscal_" + idVenda + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vendas")
public class VendaController {

    @Autowired
    private VendaService vendaService;

    @PostMapping
    public ResponseEntity<VendaResponseDTO> realizarVenda(@RequestBody @Valid VendaRequestDTO dto) {
        VendaResponseDTO vendaRealizada = vendaService.realizarVenda(dto);
        return ResponseEntity.ok(vendaRealizada);
    }

    @PostMapping("/suspender")
    public ResponseEntity<Long> suspenderVenda(@RequestBody @Valid VendaRequestDTO dto) {
        Venda vendaSuspensa = vendaService.suspenderVenda(dto);
        return ResponseEntity.ok(vendaSuspensa.getIdVenda());
    }

    @GetMapping("/suspensas")
    public ResponseEntity<List<VendaResponseDTO>> listarVendasSuspensas() {
        return ResponseEntity.ok(vendaService.listarVendasSuspensas());
    }

    @PostMapping("/{id}/efetivar")
    public ResponseEntity<Venda> efetivarVenda(@PathVariable Long id) {
        Venda venda = vendaService.efetivarVenda(id);
        return ResponseEntity.ok(venda);
    }

    @GetMapping
    public ResponseEntity<Page<VendaResponseDTO>> listarVendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @PageableDefault(size = 20, sort = "dataVenda") Pageable pageable) {

        return ResponseEntity.ok(vendaService.listarVendas(inicio, fim, pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarVenda(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String motivo = payload.get("motivo");
        vendaService.cancelarVenda(id, motivo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendaResponseDTO> buscarPorId(@PathVariable Long id) {
        Venda venda = vendaService.buscarVendaComItens(id);

        // AQUI ESTÁ A MÁGICA: Convertemos a Venda "suja" no DTO "limpo" antes de enviar para a tela
        VendaResponseDTO dto = new VendaResponseDTO(venda);

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/email")
    public ResponseEntity<Void> enviarNotaPorEmail(@PathVariable Long id, @RequestBody java.util.Map<String, String> payload) {
        String email = payload.get("email");
        vendaService.enviarEmailComXmlEPdf(id, email);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{id}/email-documento", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> enviarEmailComPdfDoFrontend(
            @PathVariable Long id,
            @RequestParam("email") String email,
            @RequestParam("pdf") org.springframework.web.multipart.MultipartFile pdf) {

        vendaService.enviarEmailComDocumentoFrontend(id, email, pdf);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<HistoricoCompraDTO>> buscarHistoricoPorCliente(@PathVariable Long clienteId) {
        List<HistoricoCompraDTO> historico = vendaService.buscarHistoricoPorCliente(clienteId);
        return ResponseEntity.ok(historico);
    }

    // ========================================================================
    // 🔥 NOVAS ROTAS PARA DOWNLOAD DE ARQUIVOS (DANFE E XML) PELA TELA DE CRM
    // ========================================================================

    @GetMapping(value = "/{id}/danfe", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Gera e baixa o PDF (DANFE/Cupom) da venda")
    public ResponseEntity<byte[]> baixarDanfe(@PathVariable Long id) {
        // Busca os bytes do PDF gerado no Service
        byte[] pdfBytes = vendaService.gerarPdfVenda(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "danfe_venda_" + id + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @GetMapping(value = "/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Baixa o XML autorizado da SEFAZ da venda")
    public ResponseEntity<byte[]> baixarXml(@PathVariable Long id) {
        // Busca a String do XML no Service e converte para bytes
        byte[] xmlBytes = vendaService.obterXmlVenda(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setContentDispositionFormData("attachment", "nota_venda_" + id + ".xml");

        return ResponseEntity.ok().headers(headers).body(xmlBytes);
    }
}
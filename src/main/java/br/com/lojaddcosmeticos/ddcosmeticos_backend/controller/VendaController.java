package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Frente de Caixa (PDV)", description = "Emissão de Cupons, Suspensões e Trâmites Fiscais")
public class VendaController {

    @Autowired
    private VendaService vendaService;

    @PostMapping
    @Operation(summary = "Realiza a transação final de venda e envia para SEFAZ")
    public ResponseEntity<VendaResponseDTO> realizarVenda(@RequestBody @Valid VendaRequestDTO dto) {
        return ResponseEntity.ok(vendaService.realizarVenda(dto));
    }

    @PostMapping("/suspender")
    @Operation(summary = "Coloca a venda em Standby (Modo de Espera no PDV)")
    public ResponseEntity<Long> suspenderVenda(@RequestBody @Valid VendaRequestDTO dto) {
        return ResponseEntity.ok(vendaService.suspenderVenda(dto).getIdVenda());
    }

    @GetMapping("/suspensas")
    @Operation(summary = "Recupera todas as pré-vendas/vendas em pausa no dia")
    public ResponseEntity<List<VendaResponseDTO>> listarVendasSuspensas() {
        return ResponseEntity.ok(vendaService.listarVendasSuspensas());
    }

    @PostMapping("/{id}/efetivar")
    @Operation(summary = "Converte uma venda Suspensa em venda Autorizada")
    public ResponseEntity<Venda> efetivarVenda(@PathVariable Long id) {
        return ResponseEntity.ok(vendaService.efetivarVenda(id));
    }

    @GetMapping
    @Operation(summary = "Histórico de Fechos de Caixa")
    public ResponseEntity<Page<VendaResponseDTO>> listarVendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @PageableDefault(size = 20, sort = "dataVenda") Pageable pageable) {
        return ResponseEntity.ok(vendaService.listarVendas(inicio, fim, pageable));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelamento da NF-e com evento transmitido à Autoridade Tributária")
    public ResponseEntity<Void> cancelarVenda(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        vendaService.cancelarVenda(id, payload.get("motivo"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Reimprime a via do Cliente (Resumo no Frontend)")
    public ResponseEntity<VendaResponseDTO> buscarPorId(@PathVariable Long id) {
        Venda venda = vendaService.buscarVendaComItens(id);
        return ResponseEntity.ok(new VendaResponseDTO(venda));
    }

    @PostMapping("/{id}/email")
    @Operation(summary = "Reenvia cupom digitalizado para o cliente")
    public ResponseEntity<Void> enviarNotaPorEmail(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        vendaService.enviarEmailComXmlEPdf(id, payload.get("email"));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{id}/email-documento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Anexa documento físico (ex: Comprovativo TEF) e reenvia por email")
    public ResponseEntity<Void> enviarEmailComPdfDoFrontend(
            @PathVariable Long id,
            @RequestParam("email") String email,
            @RequestParam("pdf") org.springframework.web.multipart.MultipartFile pdf) {
        vendaService.enviarEmailComDocumentoFrontend(id, email, pdf);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/cliente/{clienteId}")
    @Operation(summary = "Rastreia os hábitos e CRM de compras do Cliente")
    public ResponseEntity<List<HistoricoCompraDTO>> buscarHistoricoPorCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(vendaService.buscarHistoricoPorCliente(clienteId));
    }

    @GetMapping(value = "/{id}/danfe", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Gera e baixa o PDF (DANFE/Cupom) da venda autorizada")
    public ResponseEntity<byte[]> baixarDanfe(@PathVariable Long id) {
        byte[] pdfBytes = vendaService.gerarPdfVenda(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "danfe_venda_" + id + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @GetMapping(value = "/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Baixa o XML assinado pela SEFAZ de Pernambuco")
    public ResponseEntity<byte[]> baixarXml(@PathVariable Long id) {
        byte[] xmlBytes = vendaService.obterXmlVenda(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setContentDispositionFormData("attachment", "nota_venda_" + id + ".xml");
        return ResponseEntity.ok().headers(headers).body(xmlBytes);
    }
}
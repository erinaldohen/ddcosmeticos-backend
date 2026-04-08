package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

    // Dentro do seu VendaController.java

    @GetMapping("/{id}")
    public ResponseEntity<VendaResponseDTO> buscarPorId(@PathVariable Long id) {
        Venda venda = vendaService.buscarVendaComItens(id); // ou buscarPorId, dependendo de como você nomeou

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
}
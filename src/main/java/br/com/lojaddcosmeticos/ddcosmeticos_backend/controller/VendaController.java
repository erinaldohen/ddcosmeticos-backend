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

    // 1. Realizar Venda (Finalizar) - OK (Retorna DTO)
    @PostMapping
    public ResponseEntity<VendaResponseDTO> realizarVenda(@RequestBody @Valid VendaRequestDTO dto) {
        VendaResponseDTO vendaRealizada = vendaService.realizarVenda(dto);
        return ResponseEntity.ok(vendaRealizada);
    }

    // 2. Suspender Venda (Pausar - F2) - OK (Retorna ID)
    @PostMapping("/suspender")
    public ResponseEntity<Long> suspenderVenda(@RequestBody @Valid VendaRequestDTO dto) {
        Venda vendaSuspensa = vendaService.suspenderVenda(dto);
        return ResponseEntity.ok(vendaSuspensa.getIdVenda());
    }

    // 3. Listar Vendas Suspensas - OK (Retorna Lista DTO)
    @GetMapping("/suspensas")
    public ResponseEntity<List<VendaResponseDTO>> listarVendasSuspensas() {
        return ResponseEntity.ok(vendaService.listarVendasSuspensas());
    }

    // 4. Efetivar Venda Suspensa - ALERTA (Retorna Entidade)
    // Se o frontend esperar campos específicos que só existem no DTO (ex: listas formatadas), pode quebrar.
    // Mas se o front lida bem com o objeto Venda puro, ok.
    @PostMapping("/{id}/efetivar")
    public ResponseEntity<Venda> efetivarVenda(@PathVariable Long id) {
        Venda venda = vendaService.efetivarVenda(id);
        return ResponseEntity.ok(venda);
    }

    // 5. Histórico de Vendas - OK (Retorna Page DTO)
    @GetMapping
    public ResponseEntity<Page<VendaResponseDTO>> listarVendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @PageableDefault(size = 20, sort = "dataVenda") Pageable pageable) {

        return ResponseEntity.ok(vendaService.listarVendas(inicio, fim, pageable));
    }

    // 6. Cancelar Venda - OK
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarVenda(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String motivo = payload.get("motivo");
        vendaService.cancelarVenda(id, motivo);
        return ResponseEntity.noContent().build();
    }

    // 7. Buscar Venda por ID - OK (Retorna Entidade Carregada)
    // O Service garante que Itens e Pagamentos estão carregados (Hibernate.initialize)
    @GetMapping("/{id}")
    public ResponseEntity<Venda> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(vendaService.buscarVendaComItens(id));
    }
}
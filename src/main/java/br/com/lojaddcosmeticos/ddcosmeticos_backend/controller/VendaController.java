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
// @CrossOrigin(origins = "*") // Descomente se tiver problemas de CORS
public class VendaController {

    @Autowired
    private VendaService vendaService;

    // 1. Realizar Venda (Finalizar)
    // O Service retorna DTO, repassa DTO.
    @PostMapping
    public ResponseEntity<VendaResponseDTO> realizarVenda(@RequestBody @Valid VendaRequestDTO dto) {
        VendaResponseDTO vendaRealizada = vendaService.realizarVenda(dto);
        return ResponseEntity.ok(vendaRealizada);
    }

    // 2. Suspender Venda (Pausar - F2)
    // O Service retorna a Entidade Venda. O front só precisa do ID para confirmação.
    @PostMapping("/suspender")
    public ResponseEntity<Long> suspenderVenda(@RequestBody @Valid VendaRequestDTO dto) {
        Venda vendaSuspensa = vendaService.suspenderVenda(dto);
        return ResponseEntity.ok(vendaSuspensa.getIdVenda());
    }

    // 3. Listar Vendas Suspensas (Fila de Espera)
    // O Service retorna List<VendaResponseDTO>.
    @GetMapping("/suspensas")
    public ResponseEntity<List<VendaResponseDTO>> listarVendasSuspensas() {
        return ResponseEntity.ok(vendaService.listarVendasSuspensas());
    }

    // 4. Efetivar Venda Suspensa (Retomada)
    // Transforma uma venda "EM_ESPERA" em "FINALIZADA" (ou Pendente NFCe)
    @PostMapping("/{id}/efetivar")
    public ResponseEntity<Venda> efetivarVenda(@PathVariable Long id) {
        // Nota: O service retorna Entidade aqui. Idealmente seria DTO,
        // mas mantemos a compatibilidade com o Service atual.
        Venda venda = vendaService.efetivarVenda(id);
        return ResponseEntity.ok(venda);
    }

    // 5. Histórico de Vendas (Tela de Consultas)
    @GetMapping
    public ResponseEntity<Page<VendaResponseDTO>> listarVendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @PageableDefault(size = 20, sort = "dataVenda") Pageable pageable) {

        return ResponseEntity.ok(vendaService.listarVendas(inicio, fim, pageable));
    }

    // 6. Cancelar Venda
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarVenda(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String motivo = payload.get("motivo");
        vendaService.cancelarVenda(id, motivo);
        return ResponseEntity.noContent().build();
    }

    // 7. Buscar Venda por ID (Detalhes / Reimpressão)
    @GetMapping("/{id}")
    public ResponseEntity<Venda> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(vendaService.buscarVendaComItens(id));
    }
}
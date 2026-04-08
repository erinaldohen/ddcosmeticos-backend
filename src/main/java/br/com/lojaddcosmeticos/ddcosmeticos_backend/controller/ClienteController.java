package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AnaliseCreditoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ClienteDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ClienteRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes", description = "Gestão de Clientes e Limites de Crédito")
public class ClienteController {

    @Autowired
    private ClienteService clienteService;
    @Autowired
    private ClienteRepository clienteRepository;

    @GetMapping
    @Operation(summary = "Listar Clientes")
    public ResponseEntity<Page<ClienteDTO>> listar(
            @RequestParam(required = false) String termo,
            Pageable pageable) {
        return ResponseEntity.ok(clienteService.listar(termo, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @GetMapping("/cpf/{cpf}")
    public ResponseEntity<ClienteDTO> buscarPorCpf(@PathVariable String cpf) {
        return ResponseEntity.ok(clienteService.buscarPorDocumento(cpf));
    }

    @PostMapping
    @Operation(summary = "Cadastrar Cliente")
    public ResponseEntity<ClienteDTO> cadastrar(@RequestBody @Valid ClienteDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.salvar(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar Cliente")
    public ResponseEntity<ClienteDTO> atualizar(@PathVariable Long id, @RequestBody @Valid ClienteDTO dto) {
        return ResponseEntity.ok(clienteService.atualizar(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Ativar/Inativar Cliente")
    public ResponseEntity<Void> alternarStatus(@PathVariable Long id) {
        clienteService.alternarStatus(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/analise-credito/{documento}")
    public ResponseEntity<AnaliseCreditoDTO> analisarCredito(@PathVariable String documento) {
        try {
            AnaliseCreditoDTO analise = clienteService.analisarCredito(documento);
            return ResponseEntity.ok(analise);
        } catch (br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/telefone/{telefone}")
    public ResponseEntity<Cliente> buscarPorTelefone(@PathVariable String telefone) {
        String telLimpo = telefone.replaceAll("\\D", "");
        return clienteRepository.findByTelefone(telLimpo)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 🔥 MUDANÇA: Otimizado. Sem Stream pesada na tabela inteira.
    @GetMapping("/documento/{documento}")
    public ResponseEntity<Cliente> buscarPorDocumentoExato(@PathVariable String documento) {
        String docLimpo = documento.replaceAll("\\D", "");
        return clienteRepository.findByDocumento(docLimpo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
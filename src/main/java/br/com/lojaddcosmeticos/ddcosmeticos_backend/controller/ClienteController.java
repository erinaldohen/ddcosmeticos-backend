package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AnaliseCreditoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ClienteDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
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

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes", description = "Gestão de Clientes e Limites de Crédito")
public class ClienteController {

    @Autowired
    private ClienteService clienteService;

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
            // Retorna 404 se o cliente não existir, para o React forçar o cadastro
            return ResponseEntity.notFound().build();
        }
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AnaliseCreditoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ClienteDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ClienteRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes (CRM)", description = "Gestão de Cadastro de Clientes e Motor de Análise de Crédito")
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteRepository clienteRepository;

    @GetMapping
    @Operation(summary = "Listar Clientes", description = "Lista paginada com suporte a filtro de tipo (Pessoa Física vs Pessoa Jurídica B2B)")
    public ResponseEntity<Page<ClienteDTO>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) String tipo,
            Pageable pageable) {

        if (tipo != null && !tipo.isBlank()) {
            return ResponseEntity.ok(clienteService.listarPorTipo(tipo, pageable));
        }
        return ResponseEntity.ok(clienteService.listar(termo, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar Cliente por ID")
    public ResponseEntity<ClienteDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @GetMapping("/cpf/{cpf}")
    @Operation(summary = "Busca de Ficha Cadastral via Documento (CPF/CNPJ)")
    public ResponseEntity<ClienteDTO> buscarPorCpf(@PathVariable String cpf) {
        return ResponseEntity.ok(clienteService.buscarPorDocumento(cpf));
    }

    @PostMapping
    @Operation(summary = "Cadastrar Novo Cliente")
    public ResponseEntity<ClienteDTO> cadastrar(@RequestBody @Valid ClienteDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.salvar(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar Cliente Existente")
    public ResponseEntity<ClienteDTO> atualizar(@PathVariable Long id, @RequestBody @Valid ClienteDTO dto) {
        return ResponseEntity.ok(clienteService.atualizar(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Ativar ou Bloquear Cliente (Inadimplência)")
    public ResponseEntity<Void> alternarStatus(@PathVariable Long id) {
        clienteService.alternarStatus(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/analise-credito/{documento}")
    @Operation(summary = "Motor de Crédito (IA)", description = "Verifica score interno, atrasos e gera um limite aprovado de venda a prazo (fiado) sugerido.")
    public ResponseEntity<AnaliseCreditoDTO> analisarCredito(@PathVariable String documento) {
        try {
            return ResponseEntity.ok(clienteService.analisarCredito(documento));
        } catch (br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/telefone/{telefone}")
    @Operation(summary = "Identificação Rápida via Telefone (Caller ID / WhatsApp)")
    public ResponseEntity<Cliente> buscarPorTelefone(@PathVariable String telefone) {
        String telLimpo = telefone.replaceAll("\\D", "");
        return clienteRepository.findByTelefone(telLimpo).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/documento/{documento}")
    @Operation(summary = "Verificação Rápida Otimizada por Documento")
    public ResponseEntity<Cliente> buscarPorDocumentoExato(@PathVariable String documento) {
        String docLimpo = documento.replaceAll("\\D", "");
        return clienteRepository.findByDocumento(docLimpo).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO; // Importante: Importar o DTO
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendas")
public class VendaController {

    @Autowired
    private VendaService vendaService;

    /**
     * Registra uma nova venda.
     * O método agora espera e retorna o DTO de Resposta (VendaResponseDTO).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    // MUDANÇA 1: O retorno do ResponseEntity agora é <VendaResponseDTO>
    public ResponseEntity<VendaResponseDTO> registrarVenda(@RequestBody @Valid VendaRequestDTO requestDTO) {

        // MUDANÇA 2: A variável 'response' recebe um VendaResponseDTO (que vem do Service)
        VendaResponseDTO response = vendaService.registrarVenda(requestDTO);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
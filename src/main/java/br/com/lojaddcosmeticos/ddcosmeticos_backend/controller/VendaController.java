// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/controller/VendaController.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller responsável pelos endpoints da API de Vendas (PDV).
 */
@RestController
@RequestMapping("/api/v1/vendas")
public class VendaController {

    @Autowired
    private VendaService vendaService;

    /**
     * Endpoint para registrar uma nova venda completa.
     * @param requestDTO O DTO com os detalhes da venda e seus itens.
     * @return Uma resposta com o DTO de confirmação da venda (VendaResponseDTO).
     */
    @PostMapping
    public ResponseEntity<?> registrarVenda(@Valid @RequestBody VendaRequestDTO requestDTO) {
        try {
            Venda vendaRegistrada = vendaService.registrarVenda(requestDTO);

            // Mapeia para o DTO de Resposta
            VendaResponseDTO responseDTO = new VendaResponseDTO(
                    vendaRegistrada.getId(),
                    vendaRegistrada.getDataVenda(),
                    vendaRegistrada.getValorLiquido(),
                    vendaRegistrada.getItens().size()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);

        } catch (RuntimeException e) {
            // Captura erros de negócio (ex: Estoque Insuficiente, Produto Não Encontrado)
            // Retorna 400 Bad Request com a mensagem de erro.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
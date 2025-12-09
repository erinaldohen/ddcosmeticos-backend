// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/controller/VendaController.java (CORREÇÃO)

package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfceService; // Novo Serviço
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller responsável pelos endpoints da API de Vendas (PDV).
 * Coordena o registro da venda e a emissão da NFC-e.
 */
@RestController
@RequestMapping("/api/v1/vendas")
public class VendaController {

    @Autowired
    private VendaService vendaService;

    @Autowired
    private NfceService nfceService; // Novo serviço injetado

    /**
     * Endpoint para registrar uma nova venda completa e emitir a NFC-e.
     * @param requestDTO O DTO com os detalhes da venda e seus itens.
     * @return Uma resposta com o DTO de confirmação da venda e o status fiscal.
     */
    @PostMapping
    public ResponseEntity<?> registrarVenda(@Valid @RequestBody VendaRequestDTO requestDTO) {
        try {
            // 1. Processa Venda e Persiste (Core PDV)
            Venda vendaRegistrada = vendaService.registrarVenda(requestDTO);

            // 2. Emite o Documento Fiscal (Módulo Fiscal)
            NfceResponseDTO nfceResponse = nfceService.emitirNfce(vendaRegistrada);

            // Mapeia para o DTO de Resposta Consolidada
            VendaResponseDTO responseDTO = new VendaResponseDTO(
                    vendaRegistrada.getId(),
                    vendaRegistrada.getDataVenda(),
                    vendaRegistrada.getValorLiquido(),
                    vendaRegistrada.getItens().size()
            );

            // Estrutura de resposta mais completa (incluindo o fiscal)
            return ResponseEntity.status(HttpStatus.CREATED).body(new VendaFiscalResponse(responseDTO, nfceResponse));

        } catch (RuntimeException e) {
            // Captura erros de negócio (Estoque Insuficiente, Produto Não Encontrado, Erro Fiscal)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * Classe de envelopamento para retornar a resposta da Venda e o status Fiscal.
     * Deve ser criada como um novo DTO para evitar confusão.
     */
    @Data
    private static class VendaFiscalResponse {
        private final VendaResponseDTO venda;
        private final NfceResponseDTO fiscal;
    }
}
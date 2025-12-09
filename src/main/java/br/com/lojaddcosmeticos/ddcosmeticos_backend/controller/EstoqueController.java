// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/controller/EstoqueController.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller responsável pelos endpoints do Módulo de Estoque.
 * Atualmente usado para registrar entradas (com recalculo de PMP).
 */
@RestController
@RequestMapping("/api/v1/estoque")
public class EstoqueController {

    @Autowired
    private EstoqueService estoqueService;

    /**
     * Endpoint para registrar a entrada de produtos no estoque (simulação de NF de Compra).
     * Recalcula o Preço Médio Ponderado (PMP).
     * @param requestDTO O DTO com os detalhes da entrada.
     * @return O ProdutoDTO atualizado.
     */
    @PostMapping("/entrada")
    public ResponseEntity<?> registrarEntrada(@Valid @RequestBody EstoqueRequestDTO requestDTO) {
        try {
            Produto produtoAtualizado = estoqueService.registrarEntrada(requestDTO);

            // Retorna o DTO do Produto para mostrar o novo PMP e estoque
            ProdutoDTO responseDTO = new ProdutoDTO(produtoAtualizado);

            return ResponseEntity.status(HttpStatus.OK).body(responseDTO);

        } catch (RuntimeException e) {
            // Captura erros (ex: Produto Não Encontrado)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
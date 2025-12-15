package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vendas")
public class VendaController {

    @Autowired
    private VendaService vendaService;

    /**
     * Realiza uma venda (PDV).
     * Baixa estoque E gera contas a receber automaticamente.
     */
    @PostMapping
    public ResponseEntity<Venda> realizarVenda(@RequestBody @Valid VendaRequestDTO dto) {
        // Linha 33 provavelmente estava chamando o método antigo ou com parâmetros errados.
        // Agora passamos o DTO completo.
        Venda novaVenda = vendaService.realizarVenda(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(novaVenda);
    }
}
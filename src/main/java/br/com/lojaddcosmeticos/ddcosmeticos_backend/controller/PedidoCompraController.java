package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.PedidoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PedidoCompra;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PedidoCompraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/compras/pedidos")
public class PedidoCompraController {

    @Autowired
    private PedidoCompraService pedidoService;

    @PostMapping("/simular")
    public ResponseEntity<PedidoCompra> criarSimulacao(@RequestBody PedidoCompraDTO dto) {
        PedidoCompra pedido = pedidoService.criarSimulacao(dto);
        return ResponseEntity.ok(pedido);
    }

    @PostMapping("/{id}/receber")
    public ResponseEntity<Void> receberPedido(
            @PathVariable Long id,
            @RequestParam String nf,
            @RequestParam(required = false) java.time.LocalDate vencimento) {

        pedidoService.receberMercadoria(id, nf, vencimento);
        return ResponseEntity.ok().build();
    }
}
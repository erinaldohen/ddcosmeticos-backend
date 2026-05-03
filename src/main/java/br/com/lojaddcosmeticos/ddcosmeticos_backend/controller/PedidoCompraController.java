package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.PedidoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PedidoCompra;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PedidoCompraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/compras/pedidos")
@Tag(name = "Pedidos de Compra (Drafts)", description = "Simulação e Recebimento de Cargas de Fornecedores sem XML")
public class PedidoCompraController {

    @Autowired private PedidoCompraService pedidoService;

    @PostMapping("/simular")
    @Operation(summary = "Criar Espelho/Draft de Compra", description = "Regista os produtos comprados e projeta o pagamento.")
    public ResponseEntity<PedidoCompra> criarSimulacao(@RequestBody PedidoCompraDTO dto) {
        return ResponseEntity.ok(pedidoService.criarSimulacao(dto));
    }

    @PostMapping("/{id}/receber")
    @Operation(summary = "Rececionar Mercadoria na Loja", description = "Dá entrada de stock físico aos produtos do pedido e cria título financeiro (Conta a Pagar).")
    public ResponseEntity<Void> receberPedido(
            @PathVariable Long id,
            @RequestParam String nf,
            @RequestParam(required = false) java.time.LocalDate vencimento) {

        pedidoService.receberMercadoria(id, nf, vencimento);
        return ResponseEntity.ok().build();
    }
}
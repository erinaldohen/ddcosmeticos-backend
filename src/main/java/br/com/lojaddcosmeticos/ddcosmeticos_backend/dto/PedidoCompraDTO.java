package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PedidoCompraDTO {
    private String fornecedorNome;
    private String ufOrigem;  // Onde estou comprando?
    private String ufDestino; // Para onde vai? (Geralmente "PE")
    private List<ItemCompraDTO> itens;
}
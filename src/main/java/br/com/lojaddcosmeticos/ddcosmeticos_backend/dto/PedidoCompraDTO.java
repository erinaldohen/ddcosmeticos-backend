package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PedidoCompraDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;
    private String fornecedorNome;
    private String ufOrigem;  // Onde estou comprando?
    private String ufDestino; // Para onde vai? (Geralmente "PE")
    private List<ItemCompraDTO> itens;
}
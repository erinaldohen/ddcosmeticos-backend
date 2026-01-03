package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class SugestaoCompraDTO {
    private String codigoBarras;
    private String nomeProduto;
    private String marca;
    private Integer estoqueAtual;
    private Integer estoqueMinimoCalculado;

    // O coração da IA: Quanto comprar e qual a urgência
    private Integer quantidadeSugeridaCompra;
    private String nivelUrgencia; // "CRÍTICO", "ALERTA", "NORMAL"
    private BigDecimal custoEstimadoPedido;
}
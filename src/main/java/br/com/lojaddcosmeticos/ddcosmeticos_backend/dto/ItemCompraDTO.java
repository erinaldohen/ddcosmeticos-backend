package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ItemCompraDTO {
    private String codigoBarras;
    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal mva; // MVA do produto (importante para cosméticos)
}
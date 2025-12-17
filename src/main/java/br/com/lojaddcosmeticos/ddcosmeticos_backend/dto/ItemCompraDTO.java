package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ItemCompraDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;
    private String codigoBarras;
    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal mva; // MVA do produto (importante para cosméticos)
}
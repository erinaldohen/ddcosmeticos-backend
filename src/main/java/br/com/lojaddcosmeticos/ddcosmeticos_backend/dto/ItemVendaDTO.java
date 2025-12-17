package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ItemVendaDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Código de barras obrigatório")
    private String codigoBarras;

    @NotNull(message = "Quantidade obrigatória")
    @DecimalMin(value = "0.001", message = "Quantidade deve ser maior que zero")
    private BigDecimal quantidade;
}
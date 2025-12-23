package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AjusteEstoqueDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    @NotNull(message = "O código de barras é obrigatório")
    private String codigoBarras;

    @NotNull(message = "A quantidade é obrigatória")
    @Positive(message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    private String motivo;
}
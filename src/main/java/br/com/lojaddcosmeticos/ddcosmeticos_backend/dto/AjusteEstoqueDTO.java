package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
public class AjusteEstoqueDTO {

    @NotNull(message = "O código de barras é obrigatório")
    private String codigoBarras;

    @NotNull(message = "A quantidade é obrigatória")
    @Positive(message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @NotNull(message = "O tipo de movimento é obrigatório (ENTRADA, SAIDA, PERDA, SOBRA)")
    private String tipoMovimento; // <--- O erro estava aqui (faltava este campo)

    private String motivo;
}
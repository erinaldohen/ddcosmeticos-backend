package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AjusteEstoqueDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "O código de barras é obrigatório")
    private String codigoBarras;

    @NotNull(message = "A quantidade é obrigatória")
    @Positive(message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    /**
     * Campo principal da nova lógica.
     * Deve receber valores como: "AJUSTE_SOBRA", "AJUSTE_PERDA", "USO_INTERNO".
     */
    @NotBlank(message = "O motivo do ajuste é obrigatório (ex: AJUSTE_SOBRA, AJUSTE_PERDA)")
    private String motivo;

    /**
     * @deprecated O sistema agora calcula Entrada/Saída automaticamente baseado no motivo.
     * Mantido apenas para não quebrar integrações antigas, mas pode ser enviado como nulo.
     */
    private String tipoMovimento;
}
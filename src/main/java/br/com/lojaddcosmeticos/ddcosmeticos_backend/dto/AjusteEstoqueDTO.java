package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
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

    @NotNull(message = "A quantidade do ajuste é obrigatória")
    @Positive(message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @NotNull(message = "O motivo do ajuste é obrigatório")
    private MotivoMovimentacaoDeEstoque motivo;

    private String observacao;
}
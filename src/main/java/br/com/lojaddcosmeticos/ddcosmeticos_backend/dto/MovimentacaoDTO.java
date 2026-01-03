package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MovimentacaoDTO {

    @NotNull
    @Positive
    private BigDecimal valor;

    @NotNull
    private TipoMovimentacaoCaixa tipo; // SANGRIA ou SUPRIMENTO

    private String motivo;
}
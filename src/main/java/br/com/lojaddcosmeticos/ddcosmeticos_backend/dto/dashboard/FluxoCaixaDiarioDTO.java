package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

import lombok.Builder;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record FluxoCaixaDiarioDTO(
        LocalDate data,
        BigDecimal aReceber,
        BigDecimal aPagar,
        BigDecimal saldoPrevisto // Receber - Pagar
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
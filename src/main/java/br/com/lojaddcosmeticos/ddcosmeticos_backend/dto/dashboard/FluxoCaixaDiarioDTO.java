package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class FluxoCaixaDiarioDTO {
    private LocalDate data;
    private BigDecimal aReceber;
    private BigDecimal aPagar;
    private BigDecimal saldoPrevisto; // Receber - Pagar
}
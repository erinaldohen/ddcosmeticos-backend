package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
public record FechoCaixaDTO(
        LocalDate data,
        int totalVendas,
        BigDecimal faturamentoBruto,
        BigDecimal totalDescontos,
        BigDecimal faturamentoLiquido,
        List<ResumoPagamentoDTO> pagamentos
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
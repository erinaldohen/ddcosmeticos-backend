package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
public record FechoCaixaDTO(
        LocalDate data,
        long totalVendas,           // Alterado para long para bater com o Repository
        BigDecimal faturamentoBruto,
        BigDecimal faturamentoLiquido, // O campo que estava faltando na linha 219
        List<ResumoPagamentoDTO> pagamentos
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
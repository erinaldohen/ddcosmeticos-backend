package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record RelatorioDiarioDTO(
        LocalDate data,
        int quantidadeVendas,
        BigDecimal faturamentoBruto,
        BigDecimal totalDescontos,
        BigDecimal faturamentoLiquido,
        BigDecimal custoMercadoriaVendida, // CMV
        BigDecimal lucroLiquido,
        Double margemLucroPorcentagem
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
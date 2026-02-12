package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record VendaDiariaDTO(
        LocalDate data,
        BigDecimal total,
        Long quantidade
) {

    // Construtor auxiliar exigido pelo Hibernate para a Query do Repository
    // O Hibernate chamará este construtor ao fazer "new package...VendaDiariaDTO(...)"
    public VendaDiariaDTO(Object data, Number total, Long quantidade) {
        this(
                converterData(data),
                total != null ? new BigDecimal(total.toString()) : BigDecimal.ZERO,
                quantidade != null ? quantidade : 0L
        );
    }

    // Método auxiliar para tratar os diferentes tipos de data antes de chamar o 'this'
    private static LocalDate converterData(Object data) {
        if (data instanceof java.sql.Date date) {
            return date.toLocalDate();
        } else if (data instanceof LocalDate date) {
            return date;
        } else if (data instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        return null; // Ou LocalDate.now() se preferir um default
    }
}
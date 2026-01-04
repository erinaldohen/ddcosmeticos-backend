package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class VendaDiariaDTO {
    private LocalDate data;
    private BigDecimal total;
    private Long quantidade;

    // Construtor exigido pelo Hibernate para a Query do Repository
    public VendaDiariaDTO(Object data, Number total, Long quantidade) {
        if (data instanceof java.sql.Date) {
            this.data = ((java.sql.Date) data).toLocalDate();
        } else if (data instanceof java.time.LocalDate) {
            this.data = (java.time.LocalDate) data;
        } else if (data instanceof java.time.LocalDateTime) {
            this.data = ((java.time.LocalDateTime) data).toLocalDate();
        }

        this.total = total != null ? new BigDecimal(total.toString()) : BigDecimal.ZERO;
        this.quantidade = quantidade != null ? quantidade : 0L;
    }
}
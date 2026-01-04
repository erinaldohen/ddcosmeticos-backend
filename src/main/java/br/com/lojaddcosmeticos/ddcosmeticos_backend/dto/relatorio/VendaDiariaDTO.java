package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.Date; // Importante para compatibilidade

@Data
@NoArgsConstructor
public class VendaDiariaDTO {
    private LocalDate data;
    private BigDecimal totalVendido;
    private Long quantidadeVendas;

    // Construtor que aceita java.sql.Date (retornado pelo H2/Hibernate) ou LocalDate
    public VendaDiariaDTO(Object data, Number totalVendido, Long quantidadeVendas) {
        if (data instanceof java.sql.Date) {
            this.data = ((java.sql.Date) data).toLocalDate();
        } else if (data instanceof LocalDate) {
            this.data = (LocalDate) data;
        }

        this.totalVendido = totalVendido != null ? new BigDecimal(totalVendido.toString()) : BigDecimal.ZERO;
        this.quantidadeVendas = quantidadeVendas != null ? quantidadeVendas : 0L;
    }
}
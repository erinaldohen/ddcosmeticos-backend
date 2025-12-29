package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class VendaPorPagamentoDTO {
    private FormaDePagamento formaPagamento;
    private BigDecimal total;
    private Long quantidade;

    public VendaPorPagamentoDTO(FormaDePagamento formaPagamento, Number total, Long quantidade) {
        this.formaPagamento = formaPagamento;
        this.total = total != null ? new BigDecimal(total.toString()) : BigDecimal.ZERO;
        this.quantidade = quantidade != null ? quantidade : 0L;
    }
}
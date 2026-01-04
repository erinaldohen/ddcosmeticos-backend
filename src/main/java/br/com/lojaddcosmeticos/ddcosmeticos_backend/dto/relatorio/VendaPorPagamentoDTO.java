package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class VendaPorPagamentoDTO {
    private FormaDePagamento formaPagamento;
    private BigDecimal valorTotal;
    private Long quantidadeVendas;

    // Construtor compatível com a Query: (Enum, BigDecimal, Long)
    public VendaPorPagamentoDTO(FormaDePagamento formaPagamento, BigDecimal valorTotal, Long quantidadeVendas) {
        this.formaPagamento = formaPagamento;
        this.valorTotal = valorTotal != null ? valorTotal : BigDecimal.ZERO;
        this.quantidadeVendas = quantidadeVendas != null ? quantidadeVendas : 0L;
    }
}
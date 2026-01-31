package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PagamentoVenda;
import java.math.BigDecimal;

public record PagamentoResponseDTO(
        FormaDePagamento formaPagamento,
        BigDecimal valor,
        Integer parcelas
) {
    // Construtor auxiliar para converter Entidade -> DTO
    public PagamentoResponseDTO(PagamentoVenda pagamento) {
        this(
                pagamento.getFormaPagamento(),
                pagamento.getValor(),
                pagamento.getParcelas() != null ? pagamento.getParcelas() : 1
        );
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecebimentoRequestDTO(
        List<PagamentoParcialDTO> pagamentos
) {
    public record PagamentoParcialDTO(BigDecimal valor, String formaPagamento) {}
}
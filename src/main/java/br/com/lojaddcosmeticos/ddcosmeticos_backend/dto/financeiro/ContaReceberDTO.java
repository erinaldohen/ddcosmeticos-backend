package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaReceberDTO(
        Long id,
        Long vendaId,
        String clienteNome,
        String clienteTelefone,
        BigDecimal valorOriginal,
        BigDecimal valorRestante,
        BigDecimal totalPago,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataVencimento,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataEmissao,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataPagamento,
        StatusConta status
) {
    public record BaixaTituloDTO(
            BigDecimal valorPago,
            FormaDePagamento formaPagamento,
            BigDecimal juros,
            BigDecimal desconto,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataPagamento
    ) {}

    public record ResumoContasDTO(
            BigDecimal totalReceber,
            BigDecimal totalVencido,
            BigDecimal recebidoHoje
    ) {}
}
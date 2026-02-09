package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaPagarDTO(
        Long id,
        String descricao, // Ex: "Conta de Luz", "Boleto Fornecedor X"
        Long fornecedorId,
        String fornecedorNome,
        BigDecimal valorTotal,
        BigDecimal valorPago,
        BigDecimal valorRestante,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataVencimento,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataEmissao,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataPagamento,
        StatusConta status
) {
    // Para cadastrar uma nova conta manualmente (ex: Aluguel)
    public record NovaContaDTO(
            String descricao,
            Long fornecedorId, // Opcional, pode ser null se for despesa geral
            BigDecimal valorOriginal,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataVencimento,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataEmissao
    ) {}

    // Para pagar a conta
    public record BaixaContaPagarDTO(
            BigDecimal valorPago,
            FormaDePagamento formaPagamento,
            BigDecimal juros, // Juros pagos (aumenta o custo)
            BigDecimal desconto, // Desconto obtido (diminui o custo)
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataPagamento
    ) {}

    // KPIs
    public record ResumoPagarDTO(
            BigDecimal totalPagar,
            BigDecimal totalVencido,
            BigDecimal pagoHoje
    ) {}
}
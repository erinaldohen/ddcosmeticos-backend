package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record RelatorioEstoqueDTO(
        String ean,
        String nome,
        Integer saldoFisicoTotal,
        Integer saldoComNota,   // Estoque Fiscal
        Integer saldoSemNota,   // Estoque Gerencial
        BigDecimal custoMedio,
        BigDecimal valorTotalEstoque // Quanto dinheiro tem parado
) {}
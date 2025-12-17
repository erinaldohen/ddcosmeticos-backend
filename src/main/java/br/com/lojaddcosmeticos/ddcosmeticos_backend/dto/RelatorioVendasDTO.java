package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RelatorioVendasDTO(
        LocalDate dataInicio,
        LocalDate dataFim,
        long quantidadeVendas,
        BigDecimal totalBruto,      // Valor de etiqueta
        BigDecimal totalDescontos,  // Descontos dados
        BigDecimal totalLiquido,    // O que entrou no caixa (Bruto - Desc)
        BigDecimal custoTotal,      // Quanto custou repor a mercadoria
        BigDecimal lucroBruto,      // (Líquido - Custo) -> O dinheiro "limpo" antes de despesas fixas
        BigDecimal margemMedia      // % de lucro sobre a venda
) {}
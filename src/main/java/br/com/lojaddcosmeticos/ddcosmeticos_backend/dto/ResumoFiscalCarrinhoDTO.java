package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

// Utilizando 'record' (Java 14+) para dados imutáveis e concisos.
// Este DTO representa o TOTAL DA VENDA no PDV.
public record ResumoFiscalCarrinhoDTO(
        BigDecimal valorTotalVenda,    // Valor bruto cobrado do cliente
        BigDecimal totalIbs,           // Soma do IBS de todos os itens (Estadual/Municipal)
        BigDecimal totalCbs,           // Soma da CBS de todos os itens (Federal)
        BigDecimal totalIs,            // Soma do Imposto Seletivo
        BigDecimal totalLiquido,       // O que sobra para a loja (Total - Impostos)
        BigDecimal aliquotaEfetivaPorcentagem // Ex: 26.5% (Média ponderada da venda)
) {}
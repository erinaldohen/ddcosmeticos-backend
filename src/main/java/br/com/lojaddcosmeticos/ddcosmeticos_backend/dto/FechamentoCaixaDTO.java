package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Record para transporte de dados do resumo de fechamento de caixa.
 * O toBuilder = true permite clonar o objeto mascarando dados (ex: Fechamento Cego).
 */
@Builder(toBuilder = true)
public record FechamentoCaixaDTO(
        Long caixaId,
        String operador,
        LocalDateTime dataAbertura,
        LocalDateTime dataFechamento,

        // Entradas e Saídas Avulsas
        BigDecimal saldoInicial,
        BigDecimal totalSuprimentos,
        BigDecimal totalSangrias,

        // Resumo de Vendas por Modalidade
        BigDecimal totalVendasDinheiro,
        BigDecimal totalVendasPix,
        BigDecimal totalVendasCredito,
        BigDecimal totalVendasDebito,
        BigDecimal totalVendasCrediario,

        // Totais e Saldos
        long quantidadeVendas,
        BigDecimal totalVendasBruto,

        // 🚨 O VALOR SENSÍVEL: O que o sistema acha que tem na gaveta física
        // Pode ser devolvido como NULL se o 'fechamentoCegoAtivo' for true
        BigDecimal saldoEsperadoDinheiroGaveta,

        // 🛡️ Flags de Segurança e Frontend
        boolean fechamentoCegoAtivo,
        String mensagemSistema
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioVendasDTO(
        LocalDate inicio,
        LocalDate fim,
        int totalVendas,
        BigDecimal faturamentoBruto,
        BigDecimal totalDescontos,
        BigDecimal faturamentoLiquido,
        BigDecimal custoMercadoria,
        BigDecimal lucroBruto,
        BigDecimal margemPorcentagem,
        List<VendasPorHoraDTO> vendasPorHora // 10º campo obrigatório
) {}
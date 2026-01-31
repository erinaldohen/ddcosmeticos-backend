package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import java.math.BigDecimal;
import java.util.List;

public record DashboardDTO(
        // Cards
        BigDecimal faturamentoHoje,
        BigDecimal faturamentoMes,
        Long vendasHoje,
        BigDecimal ticketMedioMes,

        // Gráficos
        List<VendaDiariaDTO> graficoVendas,
        List<VendaPorPagamentoDTO> graficoPagamentos,

        // Listas Extras (O que estava faltando)
        List<ProdutoRankingDTO> rankingProdutos,
        List<VendaResponseDTO> ultimasVendas
) {}
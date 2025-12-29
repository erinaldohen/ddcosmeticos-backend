package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RelatorioVendasDTO {

    // Cabeçalho
    private LocalDateTime dataGeracao;
    private String periodo;

    // Totais Gerais
    private BigDecimal faturamentoTotal;
    private Long totalVendasRealizadas;
    private BigDecimal ticketMedio;

    // Detalhamentos (Novos campos para Gráficos)
    private List<VendaDiariaDTO> evolucaoDiaria;
    private List<VendaPorPagamentoDTO> vendasPorPagamento;
    private List<ProdutoRankingDTO> produtosMaisVendidos;
}
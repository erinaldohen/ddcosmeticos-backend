package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatorioVendasDTO {

    private LocalDateTime dataGeracao;

    // Totais Gerais
    private BigDecimal totalFaturado;
    private Integer quantidadeVendas;
    private BigDecimal ticketMedio;
    private BigDecimal lucroBrutoEstimado;

    // Listas para os Gráficos (Alinhadas com o Frontend)
    private List<VendaDiariaDTO> vendasDiarias;    // Para o gráfico de Tendência
    private List<VendaPorPagamentoDTO> porPagamento; // Para o gráfico de Pizza
    private List<ProdutoRankingDTO> rankingMarcas;  // Para o gráfico de Marcas
    private List<ProdutoRankingDTO> porCategoria;   // Para o gráfico de Categorias

    // Novas métricas para o BI Avançado
    private List<TicketRangeDTO> distribuicaoTicket; // Gráfico de barras (0-50, 51-100...)
    private List<CrossSellDTO> crossSell;            // Para o gráfico de produtos vendidos juntos
}
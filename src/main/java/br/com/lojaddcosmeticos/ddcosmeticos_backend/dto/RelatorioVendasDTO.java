package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatorioVendasDTO {

    private LocalDateTime dataGeracao;

    // Totais Gerais (Nomes padronizados com o Frontend)
    private BigDecimal totalFaturado;
    private Integer quantidadeVendas;
    private BigDecimal ticketMedio;
    private BigDecimal lucroBrutoEstimado;

    // Listas para os Gráficos
    private List<VendaDiariaDTO> vendasDiarias;
    private List<VendaPorPagamentoDTO> porPagamento;
    private List<ProdutoRankingDTO> rankingMarcas;
    private List<ProdutoRankingDTO> porCategoria;
}
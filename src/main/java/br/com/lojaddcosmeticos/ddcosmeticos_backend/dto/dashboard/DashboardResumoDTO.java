package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResumoDTO {
    // Card 1: Saúde do Estoque
    private Long produtosAbaixoMinimo;
    private Long produtosEsgotados;

    // Card 2: Financeiro
    private BigDecimal valorTotalEstoqueCusto;
    private Long produtosMargemCritica;

    // Card 3: Atividade Recente
    private List<HistoricoProdutoDTO> ultimasAlteracoes;

    // Card 4: Alertas Fiscais
    private Long produtosSemNcmOuCest;
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResumoDTO {
    // --- Card 1: Saúde do Estoque ---
    private Long produtosAbaixoMinimo;
    private Long produtosEsgotados;

    // --- Card 2: Financeiro (Estoque) ---
    private BigDecimal valorTotalEstoqueCusto;
    private Long produtosMargemCritica;

    // --- Card 3: Vendas e Fluxo (Adicionados para corrigir o Teste) ---
    private Long quantidadeVendasHoje;
    private BigDecimal totalVendidoHoje;
    private BigDecimal saldoDoDia;        // (Recebido + Vendido - Pago)
    private BigDecimal totalVencidoPagar; // Contas atrasadas

    // --- Card 4: Atividade Recente ---
    private List<HistoricoProdutoDTO> ultimasAlteracoes;

    // --- Card 5: Alertas Fiscais ---
    private Long produtosSemNcmOuCest;
}
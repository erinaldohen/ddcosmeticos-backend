package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO; // <--- O IMPORT IMPORTANTE
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

    // --- Estoque ---
    private Long produtosAbaixoMinimo;
    private Long produtosEsgotados;
    private BigDecimal valorTotalEstoqueCusto;

    // --- Financeiro & Vendas ---
    private Long quantidadeVendasHoje;
    private BigDecimal totalVendidoHoje;
    private BigDecimal saldoDoDia;
    private BigDecimal totalVencidoPagar;

    // --- Fiscal & Auditoria ---
    private Long produtosMargemCritica;
    private Long produtosSemNcmOuCest;

    // CORREÇÃO AQUI: O tipo da lista deve ser AuditoriaRequestDTO
    private List<AuditoriaRequestDTO> ultimasAlteracoes;
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record FiscalDashboardDTO(
        BigDecimal totalFaturamento,
        BigDecimal totalIBS,
        BigDecimal totalCBS,
        BigDecimal totalSeletivo,
        BigDecimal totalRetido,
        Double aliquotaEfetiva,
        List<FiscalDiarioDTO> historico,
        List<FiscalDistribuicaoDTO> distribuicao
) {
    // Records internos para estruturar as listas do gráfico
    public record FiscalDiarioDTO(
            String dia,
            BigDecimal ibs,
            BigDecimal cbs,
            BigDecimal vendas
    ) {}

    public record FiscalDistribuicaoDTO(
            String name,
            BigDecimal value
    ) {}
}
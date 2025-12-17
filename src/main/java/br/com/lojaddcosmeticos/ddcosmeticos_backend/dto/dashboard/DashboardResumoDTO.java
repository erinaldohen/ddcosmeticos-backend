package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

import lombok.Builder;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Builder
public record DashboardResumoDTO(
        // 1. O Dia (Vendas)
        BigDecimal totalVendidoHoje,
        Long quantidadeVendasHoje,
        BigDecimal ticketMedioHoje,

        // 2. O Financeiro (Hoje)
        BigDecimal aPagarHoje,
        BigDecimal aReceberHoje,
        BigDecimal saldoDoDia,

        // 3. Alertas (Urgente)
        BigDecimal totalVencidoPagar,
        Long produtosAbaixoMinimo,

        // 4. Gráfico (Projeção 7 dias)
        List<FluxoCaixaDiarioDTO> projecaoSemanal
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
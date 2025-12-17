package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardResumoDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;
    // 1. O Dia (Vendas)
    private BigDecimal totalVendidoHoje;
    private Long quantidadeVendasHoje;
    private BigDecimal ticketMedioHoje;

    // 2. O Financeiro (Hoje)
    private BigDecimal aPagarHoje;
    private BigDecimal aReceberHoje;
    private BigDecimal saldoDoDia; // Receber - Pagar

    // 3. Alertas (Urgente)
    private BigDecimal totalVencidoPagar; // Contas atrasadas
    private Long produtosAbaixoMinimo;    // Precisa repor

    // 4. Gráfico (Projeção 7 dias)
    private List<FluxoCaixaDiarioDTO> projecaoSemanal;
}
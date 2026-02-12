package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardDTO(
        FinanceiroDTO financeiro,
        InventarioDTO inventario,
        List<AuditoriaRequestDTO> auditoria, // Ou use sua entidade Auditoria se preferir, mas DTO é melhor
        List<TopProdutoDTO> rankingProdutos,
        List<UltimaVendaDTO> ultimasVendas
) {
    // --- SUB-DTOS (Podem ser arquivos separados ou records internos) ---

    public record FinanceiroDTO(
            BigDecimal faturamentoHoje,
            Integer vendasHoje,
            BigDecimal ticketMedioMes,
            List<GraficoVendaDTO> graficoVendas,
            List<GraficoPagamentoDTO> graficoPagamentos
    ) {}

    public record InventarioDTO(
            long vencidos,
            long baixoEstoque
    ) {}

    public record GraficoVendaDTO(String data, BigDecimal valor) {}

    public record GraficoPagamentoDTO(br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento formaPagamento, BigDecimal valor) {}

    public record TopProdutoDTO(String nome, Long qtd, BigDecimal total) {}

    public record UltimaVendaDTO(Long id, String clienteNome, BigDecimal valorTotal, List<PagamentoResumoDTO> pagamentos) {}

    public record PagamentoResumoDTO(br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento formaPagamento, BigDecimal valor) {}

    // AuditoriaDTO você provavelmente já tem ou pode criar simples:
    public record AuditoriaDTO(String mensagem, String tipoEvento, String usuarioResponsavel, java.time.LocalDateTime dataHora) {}
}
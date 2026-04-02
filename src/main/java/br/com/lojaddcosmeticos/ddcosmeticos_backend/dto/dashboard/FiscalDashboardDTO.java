package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard;

public record FiscalDashboardDTO(
        Long produtosAtivos,
        Long totalAuditorias,
        Long alertasFiscais
) {
}
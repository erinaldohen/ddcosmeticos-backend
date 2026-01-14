package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public record CaixaDiarioDTO(
        Long id,
        String status,
        BigDecimal saldoInicial,
        BigDecimal totalVendasDinheiro,
        BigDecimal totalVendasPix,
        BigDecimal totalVendasCartao
) {
    // Construtor vazio ou estático auxiliar se necessário,
    // mas o record já resolve a transferência de dados.
}
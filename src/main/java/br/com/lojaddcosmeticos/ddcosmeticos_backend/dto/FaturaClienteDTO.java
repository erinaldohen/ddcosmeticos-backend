package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FaturaClienteDTO(
        Long idFatura,
        LocalDate dataCompra,
        LocalDate dataPagamento,
        LocalDate vencimento,
        BigDecimal valorTotal,
        BigDecimal saldoDevedor,
        String status,
        String descricao,
        long diasEmAberto,
        List<ItemFaturaDTO> itens // <-- NOVA LISTA AQUI
) {
    // Sub-record para os itens
    public record ItemFaturaDTO(String descricao, int quantidade, BigDecimal precoUnitario) {}
}
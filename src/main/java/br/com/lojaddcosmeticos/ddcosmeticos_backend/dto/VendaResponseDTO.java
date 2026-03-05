package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record VendaResponseDTO(
        Long id,
        LocalDateTime dataVenda,
        BigDecimal valorTotal,
        BigDecimal descontoTotal,
        String clienteNome,
        FormaDePagamento formaDePagamento,
        List<ItemVendaResponseDTO> itens,
        List<PagamentoResponseDTO> pagamentos,
        BigDecimal valorIbs,
        BigDecimal valorCbs,
        BigDecimal valorIs,
        BigDecimal valorLiquido, // <-- Ajustado para bater com o VendaService
        StatusFiscal status,
        String chaveAcessoNfce,  // <-- Ajustado para bater com o VendaService
        String observacao        // Adicionado como extra útil
) {
    // Construtor auxiliar que aceita a Entidade Venda
    public VendaResponseDTO(Venda venda) {
        this(
                venda.getIdVenda(),
                venda.getDataVenda(),
                venda.getValorTotal(),
                venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO,
                venda.getClienteNome(),
                venda.getFormaDePagamento(),

                venda.getItens() != null
                        ? venda.getItens().stream().map(ItemVendaResponseDTO::new).collect(Collectors.toList())
                        : List.of(),

                venda.getPagamentos() != null
                        ? venda.getPagamentos().stream().map(PagamentoResponseDTO::new).collect(Collectors.toList())
                        : List.of(),

                venda.getValorIbs() != null ? venda.getValorIbs() : BigDecimal.ZERO,
                venda.getValorCbs() != null ? venda.getValorCbs() : BigDecimal.ZERO,
                venda.getValorIs() != null ? venda.getValorIs() : BigDecimal.ZERO,
                venda.getValorLiquido() != null ? venda.getValorLiquido() : BigDecimal.ZERO,
                venda.getStatusNfce(),
                venda.getChaveAcessoNfce(),
                venda.getObservacao()
        );
    }
}
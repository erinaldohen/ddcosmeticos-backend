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
        BigDecimal valorLiquido,
        StatusFiscal status,
        String chaveAcessoNfce,
        String observacao,
        String urlQrCode,

        // 🚨 NOVOS CAMPOS: Exigidos pelo React para montar o PDF da Impressão Corretamente
        Long numeroNfce,
        Integer serieNfce,
        String tipoNota
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

                // 🚨 CORREÇÃO: Usando new java.util.ArrayList<>() para não confundir o compilador
                venda.getItens() != null
                        ? venda.getItens().stream().map(ItemVendaResponseDTO::new).collect(Collectors.toList())
                        : new java.util.ArrayList<>(),

                // 🚨 CORREÇÃO: Usando new java.util.ArrayList<>() para não confundir o compilador
                venda.getPagamentos() != null
                        ? venda.getPagamentos().stream().map(PagamentoResponseDTO::new).collect(Collectors.toList())
                        : new java.util.ArrayList<>(),

                venda.getValorIbs() != null ? venda.getValorIbs() : BigDecimal.ZERO,
                venda.getValorCbs() != null ? venda.getValorCbs() : BigDecimal.ZERO,
                venda.getValorIs() != null ? venda.getValorIs() : BigDecimal.ZERO,
                venda.getValorLiquido() != null ? venda.getValorLiquido() : BigDecimal.ZERO,
                venda.getStatusNfce(),
                venda.getChaveAcessoNfce(),
                venda.getObservacao(),
                venda.getUrlQrCode(),

                // Mapeamento extra para a Impressão e o E-mail no PDV
                venda.getNumeroNfce(),
                venda.getSerieNfce(),

                // O DTO descobre sozinho se é B2B ou B2C antes de devolver para a tela do caixa
                (venda.getClienteDocumento() != null && venda.getClienteDocumento().replaceAll("\\D", "").length() == 14) ? "NFE" : "NFCE"
        );
    }
}
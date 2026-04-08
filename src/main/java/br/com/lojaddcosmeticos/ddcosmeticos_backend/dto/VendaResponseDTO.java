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

        // Exigidos pelo React para montar o PDF da Impressão
        Long numeroNfce,
        Integer serieNfce,
        String tipoNota,

        // 🚨 NOVOS CAMPOS: Isolamento do Frontend (Evita erros de Proxy e garante dados na tela)
        String clienteDocumento,
        String clienteTelefone,
        String protocolo,
        String xmlNota,
        String nomeOperador
) {
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
                        : new java.util.ArrayList<>(),

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

                venda.getNumeroNfce(),
                venda.getSerieNfce(),
                (venda.getClienteDocumento() != null && venda.getClienteDocumento().replaceAll("\\D", "").length() == 14) ? "NFE" : "NFCE",

                // Extraindo os dados reais para o ecrã
                venda.getClienteDocumento(),
                venda.getClienteTelefone(),
                venda.getProtocolo(),
                venda.getXmlNota(),
                venda.getUsuario() != null ? venda.getUsuario().getNome() : "Sistema"
        );
    }
}
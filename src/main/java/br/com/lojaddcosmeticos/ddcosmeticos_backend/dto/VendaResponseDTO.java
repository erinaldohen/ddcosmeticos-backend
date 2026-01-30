package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VendaResponseDTO(
        Long id,
        LocalDateTime dataVenda,
        BigDecimal valorTotal,
        BigDecimal descontoTotal,
        String clienteNome,
        FormaDePagamento formaPagamento, // Mantido para compatibilidade
        List<ItemVendaResponseDTO> itens,

        // [NOVO] Lista segura de pagamentos
        List<PagamentoResponseDTO> pagamentos,

        BigDecimal valorIbs,
        BigDecimal valorCbs,
        BigDecimal valorIs,
        BigDecimal valorLiquido,
        StatusFiscal statusNfce,
        String chaveAcessoNfce
) {}
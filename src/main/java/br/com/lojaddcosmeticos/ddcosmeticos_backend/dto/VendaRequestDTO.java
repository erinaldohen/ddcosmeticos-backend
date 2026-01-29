package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(
        String clienteDocumento,
        String clienteNome,

        // Mantido para compatibilidade, mas o foco agora é a lista abaixo
        FormaDePagamento formaDePagamento,

        @NotEmpty(message = "A venda deve ter pelo menos um item")
        List<ItemVendaDTO> itens,

        // NOVO CAMPO: Lista de pagamentos do PDV
        List<PagamentoRequestDTO> pagamentos,

        BigDecimal descontoTotal,
        Boolean ehOrcamento,
        Integer quantidadeParcelas
) {}
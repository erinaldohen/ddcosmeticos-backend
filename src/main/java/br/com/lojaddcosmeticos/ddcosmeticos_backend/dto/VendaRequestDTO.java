package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(

        Long clienteId,
        String clienteDocumento,
        String clienteNome,
        String observacao,
        FormaDePagamento formaDePagamento,

        @NotEmpty(message = "A venda deve ter pelo menos um item")
        @Valid
        List<ItemVendaDTO> itens,

        @Valid
        List<PagamentoRequestDTO> pagamentos,

        // --- CAMPOS ENVIADOS PELO REACT ---
        BigDecimal subtotal,
        BigDecimal descontoTotal,
        BigDecimal totalPago,
        BigDecimal troco,
        // ----------------------------------

        Boolean ehOrcamento,
        Integer quantidadeParcelas
) {}
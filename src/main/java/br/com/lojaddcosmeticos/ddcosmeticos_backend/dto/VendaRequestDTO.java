package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(

        String clienteCpf,
        String clienteNome,

        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaDePagamento formaPagamento,

        Integer quantidadeParcelas,

        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaDTO> itens,

        @PositiveOrZero
        BigDecimal descontoTotal,

        Boolean apenasItensComNfEntrada,

        // --- NOVO CAMPO ---
        Boolean ehOrcamento // Se true, salva como Orçamento
) {}
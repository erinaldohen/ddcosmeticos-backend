package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(
        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaDePagamento formaPagamento,

        // Adicionado para suportar a lógica do Service
        Integer quantidadeParcelas,

        @NotNull(message = "O valor total da venda é obrigatório")
        BigDecimal totalVenda,

        BigDecimal descontoTotal,

        String clienteCpf,
        String clienteNome,

        // Flag para a regra de emissão híbrida de NFC-e
        boolean apenasItensComNfEntrada,

        List<PagamentoRequestDTO> pagamentos,

        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaDTO> itens // Corrigido para usar o DTO existente

) {}
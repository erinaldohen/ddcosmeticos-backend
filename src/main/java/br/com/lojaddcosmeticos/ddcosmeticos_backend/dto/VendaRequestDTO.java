package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(
        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaDePagamento formaPagamento,

        @NotNull(message = "O valor total da venda é obrigatório")
        BigDecimal totalVenda,

        BigDecimal descontoTotal,

        String clienteCpf,
        String clienteNome,
        List<PagamentoRequestDTO> pagamentos, // Certifique-se que o tipo aqui é PagamentoRequestDTO

        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaRequestDTO> itens

) {}
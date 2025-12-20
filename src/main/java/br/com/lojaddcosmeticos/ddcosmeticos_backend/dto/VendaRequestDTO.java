package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(
        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaPagamento formaPagamento,

        @NotNull(message = "O valor total da venda é obrigatório")
        BigDecimal totalVenda,

        BigDecimal descontoTotal,

        String clienteCpf,
        String clienteNome,
        List<PagamentoRequestDTO> pagamentos, // Certifique-se que o tipo aqui é PagamentoRequestDTO

        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaRequestDTO> itens

) {}
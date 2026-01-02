package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(

        @Pattern(regexp = "^[0-9]*$", message = "O documento deve conter apenas números")
        String clienteDocumento,

        String clienteNome,

        // 🚨 MUDANÇA AQUI: Deixou de ser um único Enum para ser uma Lista
        @NotEmpty(message = "Informe pelo menos uma forma de pagamento")
        List<PagamentoRequestDTO> pagamentos,

        Integer quantidadeParcelas,

        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaDTO> itens,

        @PositiveOrZero(message = "O desconto não pode ser negativo")
        BigDecimal descontoTotal,

        Boolean apenasItensComNfEntrada,

        Boolean ehOrcamento
) {}
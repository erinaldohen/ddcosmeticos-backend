package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(

        // Segurança: Garante que só chegam números, evitando SQL Injection ou erros de formatação
        @Pattern(regexp = "^[0-9]*$", message = "O documento deve conter apenas números")
        String clienteDocumento, // Unificado (CPF/CNPJ)

        String clienteNome,

        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaDePagamento formaPagamento,

        Integer quantidadeParcelas,

        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaDTO> itens,

        @PositiveOrZero(message = "O desconto não pode ser negativo")
        BigDecimal descontoTotal,

        Boolean apenasItensComNfEntrada,

        Boolean ehOrcamento
) {}
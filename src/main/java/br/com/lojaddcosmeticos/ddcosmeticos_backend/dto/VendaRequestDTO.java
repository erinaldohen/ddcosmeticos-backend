package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(

        // Documento unificado (CPF ou CNPJ)
        String clienteDocumento,

        String clienteNome,

        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaDePagamento formaPagamento,

        Integer quantidadeParcelas,

        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaDTO> itens,

        BigDecimal descontoTotal,

        Boolean apenasItensComNfEntrada,

        // Novo campo obrigatório para o fluxo de Orçamento
        Boolean ehOrcamento
) {}
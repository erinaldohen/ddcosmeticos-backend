package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

// Usando Record (Java 16+) que gera getters automáticos, ex: dto.pagamentos()
public record VendaRequestDTO(

        @Pattern(regexp = "^[0-9]*$", message = "O documento deve conter apenas números")
        String clienteDocumento,

        String clienteNome,

        // ALTERAÇÃO CRÍTICA: Mudou de "FormaDePagamento formaPagamento" para Lista
        FormaDePagamento formaDePagamento,

        Integer quantidadeParcelas,

        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaDTO> itens,

        @PositiveOrZero(message = "O desconto não pode ser negativo")
        BigDecimal descontoTotal,

        Boolean apenasItensComNfEntrada,

        Boolean ehOrcamento
) {}
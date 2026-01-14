package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(
        String clienteDocumento, // Pode ser null
        String clienteNome,

        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaDePagamento formaDePagamento, // Certifique-se que o Enum no Java tem os valores: PIX, DINHEIRO, CREDITO, DEBITO, CREDIARIO

        // O front manda 'pagamentosDetalhados', mas se não usarmos no backend agora,
        // não precisa declarar aqui (o Jackson ignora) ou pode declarar uma List genérica se quiser receber.

        @NotEmpty(message = "A venda deve ter pelo menos um item")
        List<ItemVendaDTO> itens,

        BigDecimal descontoTotal,
        Boolean ehOrcamento,
        Integer quantidadeParcelas
) {}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull; // Caso precise validar campos obrigatórios

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(

        // Novo campo para vincular cliente existente
        Long clienteId,

        // Mantidos para clientes não cadastrados ou CPF na nota
        String clienteDocumento,
        String clienteNome,

        // Novo campo para identificar Venda Suspensa ou Observações do PDV
        String observacao,

        // Campo legado (pode ser null se a lógica usar a lista 'pagamentos')
        FormaDePagamento formaDePagamento,

        @NotEmpty(message = "A venda deve ter pelo menos um item")
        @Valid // Valida os campos dentro de cada ItemVendaDTO
        List<ItemVendaDTO> itens,

        // IMPORTANTE: Sem @NotEmpty para permitir suspender venda (lista vazia)
        @Valid // Se houver pagamentos, valida os dados deles
        List<PagamentoRequestDTO> pagamentos,

        BigDecimal descontoTotal,

        Boolean ehOrcamento,

        // Usado apenas se houver parcelamento simples fora da lista de pagamentos
        Integer quantidadeParcelas
) {}
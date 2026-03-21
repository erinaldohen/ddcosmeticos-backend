package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(

        Long clienteId,
        String clienteDocumento,
        String clienteNome,
        String observacao,
        FormaDePagamento formaDePagamento,
        String clienteTelefone,

        @NotEmpty(message = "A venda deve ter pelo menos um item")
        @Valid
        List<ItemVendaDTO> itens,

        @Valid
        List<PagamentoRequestDTO> pagamentos,

        // --- CAMPOS ENVIADOS PELO REACT ---
        BigDecimal subtotal,
        BigDecimal descontoTotal,
        BigDecimal totalPago,
        BigDecimal troco,
        // ----------------------------------

        Boolean ehOrcamento,
        Integer quantidadeParcelas,

        // --- NOVO: RECEBENDO O LOG DO PDV ANTI-FRAUDE ---
        List<LogAuditoriaPDVDTO> logAuditoria
) {
        // Record interno para mapear o JSON do React: { acao: '...', detalhes: '...', hora: '...' }
        public record LogAuditoriaPDVDTO(
                String acao,
                String detalhes,
                String hora
        ) {}
}
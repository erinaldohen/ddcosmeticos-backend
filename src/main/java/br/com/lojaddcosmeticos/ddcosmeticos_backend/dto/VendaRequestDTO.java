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
        String clienteTelefone,

        // 🚨 ENDEREÇO FATIADO PARA O XML DA NF-E (MODELO 55)
        String clienteCep,
        String clienteLogradouro,
        String clienteNumero,
        String clienteBairro,
        String clienteCidade,
        String clienteUf,

        String clienteIe,
        String tipoNota,
        String idOffline,
        String observacao,
        FormaDePagamento formaDePagamento,

        @NotEmpty(message = "A venda deve ter pelo menos um item")
        @Valid List<ItemVendaDTO> itens,
        @Valid List<PagamentoRequestDTO> pagamentos,

        BigDecimal subtotal,
        BigDecimal descontoTotal,
        BigDecimal totalPago,
        BigDecimal troco,
        Boolean ehOrcamento,
        Integer quantidadeParcelas,
        List<LogAuditoriaPDVDTO> logAuditoria
) {
        public record LogAuditoriaPDVDTO(String acao, String detalhes, String hora) {}
}
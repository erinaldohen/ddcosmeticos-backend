package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Builder
public record VendaResponseDTO(
        Long idVenda,
        LocalDateTime dataVenda,
        String clienteNome,
        String clienteDocumento,
        BigDecimal valorTotal,
        BigDecimal desconto,
        Integer totalItens,
        StatusFiscal statusFiscal,
        FormaDePagamento formaPagamento,
        List<String> alertas
) {

    public static VendaResponseDTO fromEntity(Venda venda) {
        if (venda == null) return null;

        String nomeFinal = venda.getClienteNome();
        if (nomeFinal == null || nomeFinal.isEmpty()) {
            nomeFinal = (venda.getCliente() != null) ? venda.getCliente().getNome() : "Consumidor Final";
        }

        String docFinal = venda.getClienteDocumento();
        if (docFinal == null || docFinal.isEmpty()) {
            docFinal = (venda.getCliente() != null) ? venda.getCliente().getDocumento() : null;
        }

        return VendaResponseDTO.builder()
                .idVenda(venda.getId())
                .dataVenda(venda.getDataVenda())
                .clienteNome(nomeFinal)
                .clienteDocumento(docFinal)
                .valorTotal(venda.getTotalVenda() != null ? venda.getTotalVenda() : BigDecimal.ZERO)
                .desconto(venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO)
                .totalItens(venda.getItens() != null ? venda.getItens().size() : 0)
                .statusFiscal(venda.getStatusFiscal())
                .formaPagamento(venda.getFormaPagamento())
                .alertas(Collections.emptyList())
                .build();
    }
}
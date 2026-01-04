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

    /**
     * Converte a Entidade Venda para o DTO de resposta.
     * Ajustado para ler os campos 'totalVenda' e 'descontoTotal' da sua Entidade.
     */
    public static VendaResponseDTO fromEntity(Venda venda) {

        // Lógica de Prioridade para o Cliente:
        // 1. Tenta pegar o nome gravado na venda (Snapshot - mais rápido e seguro)
        // 2. Se não tiver, tenta pegar do objeto Cliente relacionado
        // 3. Se não tiver nada, retorna "Consumidor Final" ou similar
        String nomeFinal = "Cliente não identificado";
        if (venda.getClienteNome() != null && !venda.getClienteNome().isEmpty()) {
            nomeFinal = venda.getClienteNome();
        } else if (venda.getCliente() != null) {
            nomeFinal = venda.getCliente().getNome();
        }

        String docFinal = null;
        if (venda.getClienteDocumento() != null && !venda.getClienteDocumento().isEmpty()) {
            docFinal = venda.getClienteDocumento();
        } else if (venda.getCliente() != null) {
            docFinal = venda.getCliente().getDocumento(); // Assumindo que Cliente tem esse método
        }

        // Contagem de itens segura contra NullPointer
        int qtdItens = (venda.getItens() != null) ? venda.getItens().size() : 0;

        return VendaResponseDTO.builder()
                .idVenda(venda.getId())
                .dataVenda(venda.getDataVenda())
                .clienteNome(nomeFinal)
                .clienteDocumento(docFinal)

                // --- AQUI ESTAVA A DIFERENÇA DE NOMES ---
                .valorTotal(venda.getTotalVenda())       // Sua entidade usa 'totalVenda'
                .desconto(venda.getDescontoTotal())      // Sua entidade usa 'descontoTotal'
                // ----------------------------------------

                .totalItens(qtdItens)
                .statusFiscal(venda.getStatusFiscal())
                .formaPagamento(venda.getFormaPagamento())
                .alertas(Collections.emptyList())
                .build();
    }
}
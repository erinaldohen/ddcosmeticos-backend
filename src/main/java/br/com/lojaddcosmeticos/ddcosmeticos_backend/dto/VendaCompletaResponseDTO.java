package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO para visualizar todos os detalhes de uma venda já registrada.
 */
@Data
public class VendaCompletaResponseDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    private Long idVenda;
    private LocalDateTime dataVenda;
    private BigDecimal valorTotal; // Bruto (Soma dos itens sem desconto)
    private BigDecimal descontoTotal;
    private BigDecimal valorLiquido; // Valor Final a Pagar
    private List<ItemVendaResponseDTO> itens;

    public VendaCompletaResponseDTO(Venda venda) {
        this.idVenda = venda.getId();
        this.dataVenda = venda.getDataVenda();

        // --- CORREÇÃO DAS LINHAS 31, 32 e 33 ---

        // 1. O desconto na entidade chama-se 'descontoTotal'
        this.descontoTotal = venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO;

        // 2. O valor salvo no banco ('totalVenda') já é o valor LÍQUIDO (com desconto aplicado)
        this.valorLiquido = venda.getTotalVenda() != null ? venda.getTotalVenda() : BigDecimal.ZERO;

        // 3. O valor BRUTO (Total) nós calculamos revertendo a conta: Líquido + Desconto
        this.valorTotal = this.valorLiquido.add(this.descontoTotal);

        // Mapeia a lista de itens
        // Nota: Certifique-se de que ItemVendaResponseDTO tem um construtor que aceita ItemVenda
        if (venda.getItens() != null) {
            this.itens = venda.getItens().stream()
                    .map(ItemVendaResponseDTO::new)
                    .collect(Collectors.toList());
        }
    }
}
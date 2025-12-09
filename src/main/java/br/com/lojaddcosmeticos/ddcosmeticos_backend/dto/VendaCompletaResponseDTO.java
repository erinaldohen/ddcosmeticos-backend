// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/VendaCompletaResponseDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO para visualizar todos os detalhes de uma venda já registrada.
 */
@Data
public class VendaCompletaResponseDTO {

    private Long idVenda;
    private LocalDateTime dataVenda;
    private BigDecimal valorTotal; // Bruto
    private BigDecimal descontoTotal;
    private BigDecimal valorLiquido;
    private List<ItemVendaResponseDTO> itens;

    // A resposta fiscal seria adicionada aqui em um sistema real, mas
    // faremos uma consulta separada para simular a busca pelo XML ou chave.

    public VendaCompletaResponseDTO(Venda venda) {
        this.idVenda = venda.getId();
        this.dataVenda = venda.getDataVenda();
        this.valorTotal = venda.getValorTotal();
        this.descontoTotal = venda.getDesconto();
        this.valorLiquido = venda.getValorLiquido();

        // Mapeia a lista de itens da entidade para a lista de DTOs
        this.itens = venda.getItens().stream()
                .map(ItemVendaResponseDTO::new)
                .collect(Collectors.toList());
    }
}
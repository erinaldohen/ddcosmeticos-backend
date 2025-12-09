// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/VendaResponseDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta para confirmar o registro de uma venda.
 */
@Data
public class VendaResponseDTO {

    private Long idVenda;
    private LocalDateTime dataVenda;
    private BigDecimal valorLiquido;
    private int totalItens;

    /**
     * Construtor para criar um DTO de resposta a partir dos dados da venda persistida.
     * @param idVenda O ID da venda registrada.
     * @param dataVenda A data/hora do registro.
     * @param valorLiquido O valor final da transação.
     * @param totalItens O número total de itens na transação.
     */
    public VendaResponseDTO(Long idVenda, LocalDateTime dataVenda, BigDecimal valorLiquido, int totalItens) {
        this.idVenda = idVenda;
        this.dataVenda = dataVenda;
        this.valorLiquido = valorLiquido;
        this.totalItens = totalItens;
    }
}
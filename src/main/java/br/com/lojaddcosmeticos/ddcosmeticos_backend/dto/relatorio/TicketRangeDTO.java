package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketRangeDTO {
    private String range; // Ex: "0-50", "51-100"
    private Long qtd;    // Quantidade de vendas nessa faixa
}
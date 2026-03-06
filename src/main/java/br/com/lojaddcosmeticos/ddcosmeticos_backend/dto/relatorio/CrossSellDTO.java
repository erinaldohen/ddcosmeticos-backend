package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrossSellDTO {
    private String par;        // Ex: "Shampoo + Condicionador"
    private Integer conversao; // Porcentagem de vezes que saem juntos
}
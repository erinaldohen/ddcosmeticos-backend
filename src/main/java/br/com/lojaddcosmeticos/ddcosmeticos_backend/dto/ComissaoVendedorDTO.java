package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComissaoVendedorDTO {
    private Long idVendedor;
    private String nomeVendedor;
    private Integer quantidadeVendas;
    private BigDecimal valorTotalVendido;
    private BigDecimal valorBaseComissao; // Pode ser o Lucro ou o Total (já sem taxas)
    private BigDecimal valorComissao;
}
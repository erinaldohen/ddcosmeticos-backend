package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ProdutoVisualDTO {
    private Long id;
    private String codigoBarras;
    private String nome; // Descricao resumida
    private String marca;
    private BigDecimal precoVenda;
    private String urlImagem;

    // UX: Em vez de mostrar números, mostramos status visual
    private String statusEstoque; // "DISPONÍVEL", "ÚLTIMAS UNIDADES", "ESGOTADO"
    private String corStatus;     // "GREEN", "ORANGE", "RED" (Para o frontend saber a cor)
}
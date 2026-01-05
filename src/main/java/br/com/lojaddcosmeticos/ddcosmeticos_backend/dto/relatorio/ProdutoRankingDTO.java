package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ProdutoRankingDTO {
    private String nome; // O React procura por 'nome' no eixo do gráfico
    private BigDecimal faturamento; // O React procura por 'faturamento' para a barra
    private Long quantidade;

    // Construtor flexível para a Query do Repository
    public ProdutoRankingDTO(Object nome, Number faturamento, Number quantidade) {
        this.nome = nome != null ? nome.toString() : "Indefinido";
        this.faturamento = faturamento != null ? new BigDecimal(faturamento.toString()) : BigDecimal.ZERO;
        this.quantidade = quantidade != null ? quantidade.longValue() : 0L;
    }
}
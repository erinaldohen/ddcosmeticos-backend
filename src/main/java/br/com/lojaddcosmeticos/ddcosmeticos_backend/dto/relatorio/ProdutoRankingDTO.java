package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ProdutoRankingDTO {
    private String codigoBarras;
    private String nomeProduto;
    private Long quantidadeVendida;
    private BigDecimal totalFaturado;

    // Construtor flexível para o Hibernate
    public ProdutoRankingDTO(String codigoBarras, String nomeProduto, Long quantidadeVendida, Number totalFaturado) {
        this.codigoBarras = codigoBarras;
        this.nomeProduto = nomeProduto;
        this.quantidadeVendida = quantidadeVendida != null ? quantidadeVendida : 0L;
        // Converte qualquer Number para BigDecimal de forma segura
        this.totalFaturado = totalFaturado != null ? new BigDecimal(totalFaturado.toString()) : BigDecimal.ZERO;
    }
}
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

    // --- CORREÇÃO AQUI ---
    // Alterado o 3º parâmetro de Long para Number.
    // O Hibernate retorna SUM() como BigDecimal, então precisamos receber Number e converter.
    public ProdutoRankingDTO(String codigoBarras, String nomeProduto, Number quantidadeVendida, Number totalFaturado) {
        this.codigoBarras = codigoBarras;
        this.nomeProduto = nomeProduto;

        // Converte o valor do banco (BigDecimal) para Long
        this.quantidadeVendida = quantidadeVendida != null ? quantidadeVendida.longValue() : 0L;

        // Converte o valor do banco para BigDecimal
        this.totalFaturado = totalFaturado != null ? new BigDecimal(totalFaturado.toString()) : BigDecimal.ZERO;
    }
}
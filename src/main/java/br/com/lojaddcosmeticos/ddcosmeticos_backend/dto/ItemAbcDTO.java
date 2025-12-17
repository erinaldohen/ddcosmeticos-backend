package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record ItemAbcDTO(
        String codigoBarras,
        String nomeProduto,
        BigDecimal quantidadeVendida,
        BigDecimal valorTotalVendido,
        // Campos calculados no Serviço após a busca
        Double porcentagemDoFaturamento,
        Double acumulado,
        String classe // A, B ou C
) implements Serializable {
    private static final long serialVersionUID = 1L;

    // Construtor compacto para compatibilidade com a Query JPQL
    public ItemAbcDTO(String codigoBarras, String nomeProduto, BigDecimal quantidadeVendida, BigDecimal valorTotalVendido) {
        this(codigoBarras, nomeProduto, quantidadeVendida, valorTotalVendido, null, null, null);
    }
}
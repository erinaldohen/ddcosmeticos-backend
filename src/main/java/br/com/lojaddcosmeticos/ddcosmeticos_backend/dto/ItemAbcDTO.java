package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ItemAbcDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    private String codigoBarras;
    private String nomeProduto;
    private BigDecimal quantidadeVendida;
    private BigDecimal valorTotalVendido;

    // Dados calculados no Serviço
    private Double porcentagemDoFaturamento; // Quanto este item representa do total (ex: 15%)
    private Double acumulado; // Soma acumulada até este item (ex: 45%)
    private String classe; // A, B ou C

    // Construtor usado na Query do Repository (JPQL)
    public ItemAbcDTO(String codigoBarras, String nomeProduto, BigDecimal quantidadeVendida, BigDecimal valorTotalVendido) {
        this.codigoBarras = codigoBarras;
        this.nomeProduto = nomeProduto;
        this.quantidadeVendida = quantidadeVendida;
        this.valorTotalVendido = valorTotalVendido;
    }
}
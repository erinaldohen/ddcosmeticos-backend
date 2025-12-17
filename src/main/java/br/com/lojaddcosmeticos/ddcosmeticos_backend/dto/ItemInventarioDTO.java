package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;

import java.io.Serializable;
import java.math.BigDecimal;

@Builder
public record ItemInventarioDTO(
        String codigoBarras,
        String descricao,
        String unidade,
        BigDecimal quantidade,
        BigDecimal custoUnitarioPmp,
        BigDecimal valorTotalEstoque,
        String statusFiscal
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
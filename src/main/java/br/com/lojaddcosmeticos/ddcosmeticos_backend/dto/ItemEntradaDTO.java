// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/ItemEntradaDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO para representar um item dentro da Nota Fiscal de Compra.
 */
@Data
public class ItemEntradaDTO {

    private String codigoBarras;
    private BigDecimal quantidade;

    /**
     * Custo unitário do produto conforme a NF (usado para recalcular o PMP).
     */
    private BigDecimal custoUnitario;
}
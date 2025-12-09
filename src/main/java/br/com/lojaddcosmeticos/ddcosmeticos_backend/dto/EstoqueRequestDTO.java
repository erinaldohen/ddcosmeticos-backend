// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/EstoqueRequestDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO de requisição para registrar a entrada de estoque (simulando uma NF de Compra).
 */
@Data
public class EstoqueRequestDTO {

    /**
     * Código de Barras (EAN) do produto a ser movimentado.
     */
    @NotBlank(message = "O código de barras é obrigatório.")
    private String codigoBarras;

    /**
     * Quantidade de entrada do produto.
     */
    @NotNull(message = "A quantidade é obrigatória.")
    @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero.")
    private BigDecimal quantidade;

    /**
     * Custo unitário da compra (Preço de Custo na NF).
     */
    @NotNull(message = "O custo unitário é obrigatório.")
    @DecimalMin(value = "0.00", message = "O custo unitário não pode ser negativo.")
    private BigDecimal custoUnitario;
}
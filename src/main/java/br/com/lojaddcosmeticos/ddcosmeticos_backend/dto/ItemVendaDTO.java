// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/ItemVendaDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ItemVendaDTO {

    @NotBlank(message = "O código de barras do item é obrigatório.")
    private String codigoBarras;

    @NotNull(message = "A quantidade é obrigatória.")
    @DecimalMin(value = "0.001", message = "A quantidade deve ser positiva.")
    private BigDecimal quantidade;

    @NotNull(message = "O preço unitário é obrigatório.")
    @DecimalMin(value = "0.01", message = "O preço unitário deve ser positivo.")
    private BigDecimal precoUnitario;

    @NotNull(message = "O desconto do item não pode ser nulo.")
    @DecimalMin(value = "0.00", message = "O desconto do item deve ser zero ou positivo.")
    private BigDecimal descontoItem;
}
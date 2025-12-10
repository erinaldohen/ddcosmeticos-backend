// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/VendaRequestDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class VendaRequestDTO {

    @NotNull(message = "O desconto da venda não pode ser nulo.")
    @DecimalMin(value = "0.00", message = "O desconto deve ser zero ou positivo.")
    private BigDecimal desconto;

    @NotEmpty(message = "A lista de itens não pode ser vazia.")
    @Valid // Valida cada item dentro da lista
    private List<ItemVendaDTO> itens;
}
// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/VendaRequestDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de requisição para registrar uma nova venda completa.
 * Recebe a lista de itens e o desconto global da transação.
 */
@Data
public class VendaRequestDTO {

    /**
     * Lista de itens da venda.
     */
    @NotNull(message = "A venda deve conter itens.")
    @NotEmpty(message = "A lista de itens não pode estar vazia.")
    @Valid // Garante que a validação seja aplicada em cada ItemVendaRequestDTO
    private List<ItemVendaRequestDTO> itens;

    /**
     * Desconto aplicado sobre o total da venda (opcional).
     */
    @NotNull(message = "O desconto é obrigatório e deve ser zero se não houver.")
    @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.")
    private BigDecimal desconto = BigDecimal.ZERO;

    // Outros campos como forma de pagamento, cliente, etc., seriam adicionados aqui no futuro.
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor // Gera construtor com todos os argumentos
@NoArgsConstructor  // Gera construtor vazio (importante para o Jackson/JSON)
public class VendaRequestDTO {

    @NotNull(message = "O desconto da venda não pode ser nulo.")
    @DecimalMin(value = "0.00", message = "O desconto deve ser zero ou positivo.")
    private BigDecimal desconto;

    @NotEmpty(message = "A lista de itens não pode ser vazia.")
    @Valid
    private List<ItemVendaDTO> itens;

    // --- CAMPO NOVO ---
    // Obrigatório para saber se gera XML fiscal ou financeiro
    @NotNull(message = "A forma de pagamento é obrigatória")
    private String formaPagamento;
}
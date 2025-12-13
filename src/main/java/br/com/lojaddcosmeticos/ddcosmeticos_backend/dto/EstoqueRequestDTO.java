package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class EstoqueRequestDTO {

    @NotBlank(message = "Código de barras obrigatório")
    private String codigoBarras;

    @NotNull
    @DecimalMin(value = "0.01", message = "Quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @NotNull
    @DecimalMin(value = "0.01", message = "Preço de custo deve ser maior que zero")
    private BigDecimal precoCusto; // Valor pago ao fornecedor

    private String numeroNotaFiscal; // Opcional, mas recomendado para rastreio

    private String fornecedorCnpj; // Para vincular ao fornecedor (opcional por enquanto)
}
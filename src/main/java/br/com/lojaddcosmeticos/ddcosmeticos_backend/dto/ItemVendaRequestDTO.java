package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de requisição para um item individual de venda.
 * Usado para receber os dados de um produto adicionado ao carrinho pelo PDV.
 */
@Data
public class ItemVendaRequestDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    /**
     * Código de Barras (EAN) do produto. Essencial para identificar o produto no banco de dados.
     */
    @NotBlank(message = "O código de barras é obrigatório.")
    private String codigoBarras;

    /**
     * Quantidade do produto vendida.
     */
    @NotNull(message = "A quantidade é obrigatória.")
    @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero.")
    private BigDecimal quantidade;

    /**
     * Preço unitário aplicado no momento da venda (pode ser diferente do preço de tabela devido a promoções).
     */
    @NotNull(message = "O preço unitário é obrigatório.")
    @DecimalMin(value = "0.00", message = "O preço unitário não pode ser negativo.")
    private BigDecimal precoUnitario;

    /**
     * Desconto aplicado sobre este item (opcional).
     */
    private BigDecimal descontoItem = BigDecimal.ZERO;
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ItemVendaDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // Adicionado para o Service encontrar o produto (findById)
    private Long produtoId;

    // Mantido como opcional (fallback)
    private String codigoBarras;

    @NotNull(message = "Quantidade obrigatória")
    @Min(value = 1, message = "Quantidade deve ser pelo menos 1")
    // Alterado de BigDecimal para Integer para bater com a Entity ItemVenda e evitar erro de tipo
    private Integer quantidade;

    // Adicionado para permitir que o PDV envie o preço praticado (caso tenha desconto no item)
    private BigDecimal precoUnitario;
}
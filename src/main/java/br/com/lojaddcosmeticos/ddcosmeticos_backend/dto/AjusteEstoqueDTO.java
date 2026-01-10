package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AjusteEstoqueDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "O código de barras é obrigatório")
    private String codigoBarras;

    // Usei PositiveOrZero pois o ajuste pode ser para zerar o estoque (quantidade = 0)
    // Se a lógica for "Quantidade a adicionar/remover", use Positive.
    // Se for "Nova Quantidade Final" (que parece ser o caso pela lógica do Service), PositiveOrZero é melhor.
    @NotNull(message = "A nova quantidade é obrigatória")
    @PositiveOrZero(message = "A quantidade não pode ser negativa")
    private BigDecimal quantidade;

    // O motivo pode ser opcional aqui se o Service já define AJUSTE_INVENTARIO,
    // mas se o usuário escolhe no front, mantenha.
    private MotivoMovimentacaoDeEstoque motivo;

    private String observacao;
}
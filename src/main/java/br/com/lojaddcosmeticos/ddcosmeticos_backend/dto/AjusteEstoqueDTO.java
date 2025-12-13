package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AjusteEstoqueDTO {

    /**
     * O código de barras do produto a ser ajustado.
     */
    @NotBlank(message = "O código de barras é obrigatório.")
    private String codigoBarras;

    /**
     * A quantidade REAL que você contou na prateleira.
     * O sistema calculará a diferença automaticamente.
     * Ex: Se o sistema diz 10 e você digita 8, o sistema entende -2 (Perda).
     */
    @NotNull(message = "A nova quantidade real é obrigatória.")
    @DecimalMin(value = "0.0", message = "A quantidade não pode ser negativa.")
    private BigDecimal novaQuantidadeReal;

    /**
     * O motivo do ajuste. Essencial para o Relatório de Perdas.
     * Ex: "FURTO", "QUEBRA", "VALIDADE", "ERRO DE CONTAGEM".
     */
    @NotBlank(message = "O motivo do ajuste é obrigatório (Ex: Furto, Quebra).")
    private String motivo;
}
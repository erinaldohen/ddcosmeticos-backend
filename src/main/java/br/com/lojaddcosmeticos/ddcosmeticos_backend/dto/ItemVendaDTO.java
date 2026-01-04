package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemVendaDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // Opcional: Se o front mandar ID, busca por ID.
    private Long produtoId;

    // Recomendado: O PDV geralmente manda o código de barras lido pelo leitor
    private String codigoBarras;

    @NotNull(message = "A quantidade é obrigatória")
    @Positive(message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade; // CORREÇÃO: BigDecimal para alinhar com a Entity

    // Opcional: Se null, o backend usa o preço atual do cadastro.
    // Se preenchido, o backend acata (útil para descontos pontuais no PDV)
    private BigDecimal precoUnitario;
}
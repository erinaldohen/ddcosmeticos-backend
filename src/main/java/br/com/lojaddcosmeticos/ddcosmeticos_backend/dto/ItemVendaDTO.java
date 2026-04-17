package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoInfluenciaIA;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.math.BigDecimal;

public record ItemVendaDTO(
        // Opcional: Se o front mandar ID, busca por ID.
        Long produtoId,

        // Recomendado: O PDV geralmente manda o código de barras lido pelo leitor
        String codigoBarras,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser maior que zero")
        BigDecimal quantidade,

        // Opcional: Se null, o backend usa o preço atual do cadastro.
        // Se preenchido, o backend acata (útil para descontos pontuais no PDV)
        BigDecimal precoUnitario,

        // Desconto concedido especificamente neste item
        BigDecimal desconto,

        // 🔥 NOVO: Captura o impacto da Inteligência Artificial (DIRETA, INDIRETA, NENHUMA)
        TipoInfluenciaIA influenciaIA
) implements Serializable {
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;

public record ProdutoRankingDTO(
        @JsonProperty("nome")
        String produto,

        @JsonProperty("valor")
        BigDecimal valorTotal,

        Long quantidade,

        String unidade
) implements Serializable {}
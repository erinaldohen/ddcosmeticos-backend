package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;
import java.math.BigDecimal;

public record SugestaoPrecoDTO(
        String produto,
        BigDecimal custoMedio,
        BigDecimal precoAtual,
        BigDecimal precoSugerido,
        BigDecimal margemAtualPercentual,
        BigDecimal margemAlvoPercentual,
        String status // LUCRO_BOM, PREJUIZO, etc.
) {}
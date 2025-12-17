package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record InventarioResponseDTO(
        String tipoInventario, // "CONTABIL_FISCAL" ou "GERENCIAL_COMPLETO"
        LocalDateTime dataGeracao,
        int totalItens,
        BigDecimal valorTotalEstoque,
        List<ItemInventarioDTO> itens
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
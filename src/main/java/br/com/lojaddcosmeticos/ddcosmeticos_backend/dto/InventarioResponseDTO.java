package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InventarioResponseDTO {
    private String tipoInventario; // "CONTABIL_FISCAL" ou "GERENCIAL_COMPLETO"
    private LocalDateTime dataGeracao;
    private int totalItens;
    private BigDecimal valorTotalEstoque; // O valor financeiro parado na loja
    private List<ItemInventarioDTO> itens;
}
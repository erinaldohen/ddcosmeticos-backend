package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ItemInventarioDTO {
    private String codigoBarras;
    private String descricao;
    private String unidade; // UN, KG, etc.
    private BigDecimal quantidade;
    private BigDecimal custoUnitarioPmp; // Preço Médio Ponderado
    private BigDecimal valorTotalEstoque; // Qtd * Custo
    private String statusFiscal; // "FISCAL" ou "NAO_FISCAL"
}
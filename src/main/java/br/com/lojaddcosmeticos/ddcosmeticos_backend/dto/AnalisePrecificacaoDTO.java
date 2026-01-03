package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AnalisePrecificacaoDTO {
    private String codigoBarras;
    private String nomeProduto;

    private BigDecimal custoAtual;
    private BigDecimal precoVendaAtual;

    // Indicadores Financeiros
    private BigDecimal margemLucroPercentual; // Ex: 15%
    private BigDecimal lucroLiquidoDinheiro;  // Ex: R$ 5,00

    // Inteligência
    private String statusMargem; // "CRÍTICO (PREJUÍZO)", "BAIXO", "SAUDÁVEL", "EXCELENTE"
    private BigDecimal precoVendaSugerido; // Sugestão baseada em markup padrão
}
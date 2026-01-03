package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class SimulacaoTributariaDTO {
    private String nomeProduto;
    private BigDecimal precoVendaAtual;

    // Cenário Atual (Simplificado)
    private BigDecimal cargaTributariaAtualEstimada; // Aprox. 18% a 27% (ICMS + PIS/COFINS)
    private BigDecimal lucroLiquidoAtual;

    // Cenário Reforma (IBS + CBS)
    private String classificacaoReforma;
    private BigDecimal cargaTributariaReforma; // Estimada (ex: 26.5% padrão ou reduzida)
    private BigDecimal lucroLiquidoPosReforma;

    private String veredito; // "Margem Melhorou", "Margem Piorou", "Neutro"
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class RelatorioDiarioDTO {
    private LocalDate data;
    private int quantidadeVendas;

    // O que entrou no caixa
    private BigDecimal faturamentoBruto;
    private BigDecimal totalDescontos;
    private BigDecimal faturamentoLiquido;

    // O custo dos produtos que saíram (Baseado no PMP)
    private BigDecimal custoMercadoriaVendida; // CMV

    // O resultado final
    private BigDecimal lucroLiquido;
    private Double margemLucroPorcentagem;
}
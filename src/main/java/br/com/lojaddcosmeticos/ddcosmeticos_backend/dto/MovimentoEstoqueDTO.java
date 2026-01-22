package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovimentoEstoqueDTO {
    private Long id;
    private LocalDateTime dataMovimento;
    private TipoMovimentoEstoque tipo;
    private MotivoMovimentacaoDeEstoque motivo;
    private String produtoDescricao;
    private BigDecimal quantidade;
    private BigDecimal custoUnitario;
    private String documentoReferencia;
}
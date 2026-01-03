package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPrecificacao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SugestaoPrecoDTO {

    private Long id;
    private Long produtoId;
    private String nomeProduto;

    private BigDecimal custoBase;
    private BigDecimal precoVendaAtual;
    private BigDecimal precoVendaSugerido;

    private BigDecimal margemAtualPercentual;
    private BigDecimal margemSugeridaPercentual;

    private LocalDateTime dataSugestao;

    // Aqui está o segredo: O campo no DTO chama-se 'status' para o frontend,
    // mas recebe o valor de 'statusPrecificacao' da entidade.
    private StatusPrecificacao status;

    private String observacao;
}
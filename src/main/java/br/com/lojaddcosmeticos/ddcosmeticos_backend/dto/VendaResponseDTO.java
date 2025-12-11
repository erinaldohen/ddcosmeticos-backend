package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de Resposta para a Venda (o que será retornado ao cliente).
 * Ajustado para corresponder exatamente à entidade Venda.java.
 */
@Data
@NoArgsConstructor // Gera o construtor vazio (necessário para o JSON/Frameworks)
public class VendaResponseDTO {

    private Long idVenda;
    private LocalDateTime dataVenda; // Nome ajustado (era dataHora)
    private BigDecimal valorTotal;
    private BigDecimal desconto;     // Nome ajustado (era descontoGeral)
    private String operador;
    private int totalItens;

    /**
     * Construtor Personalizado: Aceita a entidade Venda e preenche os campos do DTO.
     * Resolve o erro: "actual and formal argument lists differ in length"
     */
    public VendaResponseDTO(Venda venda) {
        this.idVenda = venda.getId();
        // Usa getDataVenda() conforme definido na entidade Venda
        this.dataVenda = venda.getDataVenda();

        this.valorTotal = venda.getValorTotal();

        // Usa getDesconto() conforme definido na entidade Venda
        this.desconto = venda.getDesconto();

        // Tratamento de nulos para operador e itens
        this.operador = (venda.getOperador() != null) ? venda.getOperador().getMatricula() : "N/A";
        this.totalItens = (venda.getItens() != null) ? venda.getItens().size() : 0;
    }
}
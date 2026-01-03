package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class HistoricoProdutoDTO {
    private Integer revisaoId;
    private Date dataAlteracao;
    private String tipoRevisao;
    private String nomeNaqueleMomento;
    private BigDecimal precoVendaNaqueleMomento;
    private BigDecimal custoNaqueleMomento;
    private Integer estoqueNaqueleMomento;

    // CONSTRUTOR COM 7 ARGUMENTOS
    public HistoricoProdutoDTO(Integer revisaoId, Date dataAlteracao, String tipoRevisao,
                               String nome, BigDecimal preco, BigDecimal custo, Integer estoque) {
        this.revisaoId = revisaoId;
        this.dataAlteracao = dataAlteracao;
        this.tipoRevisao = tipoRevisao;
        this.nomeNaqueleMomento = nome;
        this.precoVendaNaqueleMomento = preco;
        this.custoNaqueleMomento = custo;
        this.estoqueNaqueleMomento = estoque;
    }
}
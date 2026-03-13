package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.util.Date;

public class HistoricoProdutoDTO {

    private Integer idRevisao;
    private Date dataRevisao;
    private String tipoAlteracao;
    private String descricao;
    private BigDecimal precoVenda;
    private BigDecimal precoCusto;
    private Integer quantidade;
    private String usuarioResponsavel;

    // CONSTRUTOR ATUALIZADO (Agora aceita os 8 parâmetros!)
    public HistoricoProdutoDTO(Integer idRevisao, Date dataRevisao, String tipoAlteracao,
                               String descricao, BigDecimal precoVenda, BigDecimal precoCusto,
                               Integer quantidade, String usuarioResponsavel) {
        this.idRevisao = idRevisao;
        this.dataRevisao = dataRevisao;

        // Traduz o ADD/MOD/DEL do Envers para português
        if ("ADD".equals(tipoAlteracao)) this.tipoAlteracao = "CRIADO";
        else if ("MOD".equals(tipoAlteracao)) this.tipoAlteracao = "ALTERADO";
        else if ("DEL".equals(tipoAlteracao)) this.tipoAlteracao = "EXCLUÍDO";
        else this.tipoAlteracao = tipoAlteracao;

        this.descricao = descricao;
        this.precoVenda = precoVenda;
        this.precoCusto = precoCusto;
        this.quantidade = quantidade;

        // Se o utilizador vier vazio, coloca "Sistema" por defeito
        this.usuarioResponsavel = (usuarioResponsavel != null && !usuarioResponsavel.isEmpty())
                ? usuarioResponsavel
                : "Sistema (XML / API)";
    }

    // Getters e Setters
    public Integer getIdRevisao() { return idRevisao; }
    public void setIdRevisao(Integer idRevisao) { this.idRevisao = idRevisao; }

    public Date getDataRevisao() { return dataRevisao; }
    public void setDataRevisao(Date dataRevisao) { this.dataRevisao = dataRevisao; }

    public String getTipoAlteracao() { return tipoAlteracao; }
    public void setTipoAlteracao(String tipoAlteracao) { this.tipoAlteracao = tipoAlteracao; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getPrecoVenda() { return precoVenda; }
    public void setPrecoVenda(BigDecimal precoVenda) { this.precoVenda = precoVenda; }

    public BigDecimal getPrecoCusto() { return precoCusto; }
    public void setPrecoCusto(BigDecimal precoCusto) { this.precoCusto = precoCusto; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public String getUsuarioResponsavel() { return usuarioResponsavel; }
    public void setUsuarioResponsavel(String usuarioResponsavel) { this.usuarioResponsavel = usuarioResponsavel; }
}
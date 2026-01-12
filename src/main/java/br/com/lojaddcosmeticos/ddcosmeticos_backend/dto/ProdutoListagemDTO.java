package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

public class ProdutoListagemDTO {
    private Long id;
    private String descricao;
    private BigDecimal precoVenda;
    private String urlImagem;
    private Integer quantidadeEmEstoque;
    private Boolean ativo;
    private String codigoBarras;
    private String marca;
    private String ncm;

    public ProdutoListagemDTO() {}

    public ProdutoListagemDTO(Long id, String descricao, BigDecimal precoVenda, String urlImagem,
                              Integer quantidadeEmEstoque, Boolean ativo, String codigoBarras,
                              String marca, String ncm) {
        this.id = id;
        this.descricao = descricao;
        this.precoVenda = precoVenda;
        this.urlImagem = urlImagem;
        this.quantidadeEmEstoque = quantidadeEmEstoque;
        this.ativo = ativo;
        this.codigoBarras = codigoBarras;
        this.marca = marca;
        this.ncm = ncm;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public BigDecimal getPrecoVenda() { return precoVenda; }
    public void setPrecoVenda(BigDecimal precoVenda) { this.precoVenda = precoVenda; }
    public String getUrlImagem() { return urlImagem; }
    public void setUrlImagem(String urlImagem) { this.urlImagem = urlImagem; }
    public Integer getQuantidadeEmEstoque() { return quantidadeEmEstoque; }
    public void setQuantidadeEmEstoque(Integer quantidadeEmEstoque) { this.quantidadeEmEstoque = quantidadeEmEstoque; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getNcm() { return ncm; }
    public void setNcm(String ncm) { this.ncm = ncm; }
}
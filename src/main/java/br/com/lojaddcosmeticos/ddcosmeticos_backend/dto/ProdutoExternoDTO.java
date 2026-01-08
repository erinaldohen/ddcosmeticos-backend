package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

public class ProdutoExternoDTO {
    private String ean;
    private String nome;
    private String ncm;
    private String cest;
    private String cst;
    private String urlImagem;
    private Double precoMedio;
    private String marca;
    private String categoria;
    private Boolean monofasico;
    private String classificacaoReforma;

    public ProdutoExternoDTO() {
    }

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getNcm() {
        return ncm;
    }

    public void setNcm(String ncm) {
        this.ncm = ncm;
    }

    public String getCest() {
        return cest;
    }

    public void setCest(String cest) {
        this.cest = cest;
    }

    public String getCst() {
        return cst;
    }

    public void setCst(String cst) {
        this.cst = cst;
    }

    public String getUrlImagem() {
        return urlImagem;
    }

    public void setUrlImagem(String urlImagem) {
        this.urlImagem = urlImagem;
    }

    public Double getPrecoMedio() {
        return precoMedio;
    }

    public void setPrecoMedio(Double precoMedio) {
        this.precoMedio = precoMedio;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public Boolean getMonofasico() {
        return monofasico;
    }

    public void setMonofasico(Boolean monofasico) {
        this.monofasico = monofasico;
    }

    public String getClassificacaoReforma() {
        return classificacaoReforma;
    }

    public void setClassificacaoReforma(String classificacaoReforma) {
        this.classificacaoReforma = classificacaoReforma;
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum TipoInfluenciaIA {
    NENHUMA("Venda orgânica, sem influência"),
    DIRETA("Produto sugerido foi aceito e vendido"),
    INDIRETA("Produto sugerido não foi aceito, mas motivou a venda de um similar (mesma categoria)");

    private final String descricao;

    TipoInfluenciaIA(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum LarguraPapel {
    MM_80("80mm"),
    MM_58("58mm");

    private final String descricao;

    LarguraPapel(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
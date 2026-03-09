package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum CanalOrigem {
    LOJA_FISICA("Loja Física"),
    WHATSAPP("WhatsApp"),
    INSTAGRAM("Instagram"),
    GOOGLE_MAPS("Google Maps"),
    E_COMMERCE("Site E-commerce"),
    OUTROS("Outros");

    private final String descricao;

    CanalOrigem(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
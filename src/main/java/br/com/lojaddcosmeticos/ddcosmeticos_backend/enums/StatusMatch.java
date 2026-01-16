package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum StatusMatch {
    MATCH_EXATO,      // Verde (EAN ou Vínculo De/Para)
    SUGESTAO_FORTE,   // Amarelo (Nome parecido + NCM igual)
    NOVO_PRODUTO      // Vermelho (Nada encontrado)
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum TipoDestinatarioSplit {
    UNIAO_FEDERAL,      // CBS e Imposto Seletivo
    ESTADO_MUNICIPIO,   // IBS
    LOJISTA,            // Valor líquido da venda
    MARKETPLACE         // Caso haja comissão de plataforma (opcional)
}
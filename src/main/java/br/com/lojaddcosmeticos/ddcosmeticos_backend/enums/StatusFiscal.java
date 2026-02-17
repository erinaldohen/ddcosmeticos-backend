package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum StatusFiscal {
    PENDENTE,
    EM_PROCESSAMENTO,
    AUTORIZADA,
    REJEITADA,
    CANCELADA,
    CONTINGENCIA, // <--- NOVO: Nota emitida offline, aguardando envio
    ERRO_CONTINGENCIA,
    ORCAMENTO,
    EM_ESPERA,
    ERRO_EMISSAO,
}
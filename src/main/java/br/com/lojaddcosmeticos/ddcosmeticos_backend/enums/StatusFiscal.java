package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum StatusFiscal {
    PENDENTE,
    CONCLUIDA,
    EM_PROCESSAMENTO,
    AUTORIZADA,
    REJEITADA,
    CANCELADA,
    CONTINGENCIA_OFFLINE, // <--- NOVO: Nota emitida offline, aguardando envio
    ERRO_CONTINGENCIA,
    ORCAMENTO,
    EM_ESPERA,
    ERRO_EMISSAO,
}
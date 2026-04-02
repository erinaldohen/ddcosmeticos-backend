package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum StatusFiscal {
    PENDENTE,              // Nota criada, aguardando envio para a SEFAZ
    EM_PROCESSAMENTO,      // Enviada, aguardando retorno (lote/assíncrono)
    AUTORIZADA,            // Sucesso: SEFAZ validou e autorizou a emissão
    REJEITADA,             // Erro de Negócio: SEFAZ negou (ex: NCM inválido, Tributação errada)
    CANCELADA,             // Nota cancelada pelo utilizador dentro do prazo legal
    CONTINGENCIA_OFFLINE,  // Nota impressa sem internet, aguardando sincronização
    ERRO_EMISSAO           // Erro Técnico: Falha de rede, certificado vencido ou indisponibilidade
}
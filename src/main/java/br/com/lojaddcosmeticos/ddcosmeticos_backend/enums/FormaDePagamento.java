package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum FormaDePagamento {
    DINHEIRO,
    PIX,

    // Mantemos as versões curtas (comuns em sistemas legados)
    CREDITO,
    DEBITO,

    // Adicionamos as versões explícitas (Melhor para relatórios fiscais)
    CARTAO_CREDITO,
    CARTAO_DEBITO,

    CREDIARIO,
    BOLETO
}
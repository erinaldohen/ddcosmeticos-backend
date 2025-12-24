package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum MotivoMovimentacaoDeEstoque {
    // Entradas
    COMPRA_FORNECEDOR,
    DEVOLUCAO_CLIENTE,
    AJUSTE_SOBRA,
    ESTOQUE_INICIAL, // <--- ADICIONADO
    AJUSTE_MANUAL,   // <--- ADICIONADO

    // Saídas
    VENDA,
    CANCELAMENTO_DE_VENDA,
    AJUSTE_PERDA,
    AJUSTE_AVARIA,
    USO_INTERNO,
    DEVOLUCAO_AO_FORNECEDOR
}
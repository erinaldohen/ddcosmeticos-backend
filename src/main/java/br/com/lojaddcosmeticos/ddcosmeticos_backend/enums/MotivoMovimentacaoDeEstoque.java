package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum MotivoMovimentacaoDeEstoque {
    // Vendas
    VENDA,
    CANCELAMENTO_DE_VENDA,

    // Compras
    COMPRA_FORNECEDOR,
    DEVOLUCAO_AO_FORNECEDOR,

    // Ajustes de Inventário (O que faltava)
    AJUSTE_SOBRA,      // Conta como ENTRADA
    AJUSTE_PERDA,      // Conta como SAÍDA
    AJUSTE_AVARIA,     // Conta como SAÍDA (Quebrou/Venceu)
    USO_INTERNO,       // Conta como SAÍDA (Usou na loja)
    DEVOLUCAO_CLIENTE; // Conta como ENTRADA
}
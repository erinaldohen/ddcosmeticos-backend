package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

/**
 * Define as modalidades de pagamento aceitas no PDV.
 */
public enum FormaDePagamento {

    // ==================================================================================
    // SESSÃO 1: PAGAMENTOS IMEDIATOS (BAIXA AUTOMÁTICA)
    // ==================================================================================
    DINHEIRO,
    PIX,
    DEBITO,

    // ==================================================================================
    // SESSÃO 2: PAGAMENTOS A PRAZO (GERAM CONTA A RECEBER)
    // ==================================================================================

    CREDITO,    // Cartão de Crédito (Garantia do Banco/Operadora)
    BOLETO,     // Boleto Bancário

    /**
     * "Fiado" ou Carnê da Loja.
     * O risco é da loja e depende do limite de crédito do cliente.
     */
    CREDIARIO
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum TipoMovimentacaoCaixa {
    SANGRIA,    // Retirada manual (para banco, pagamento)
    SUPRIMENTO, // Entrada manual (troco)
    ENTRADA,    // Vendas e Recebimentos de Crediário
    SAIDA       // Pagamento de Despesas (Contas a Pagar)
}
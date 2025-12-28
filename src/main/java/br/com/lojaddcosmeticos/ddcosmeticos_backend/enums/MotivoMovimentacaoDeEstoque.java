package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

/**
 * Define o motivo operacional da movimentação.
 * Organizado por efeito no saldo de estoque (Entrada vs Saída).
 */
public enum MotivoMovimentacaoDeEstoque {

    // --- ENTRADAS (Aumentam o Estoque) ---
    COMPRA_FORNECEDOR,      // Chegada de mercadoria (Nota de Entrada)
    DEVOLUCAO_CLIENTE,      // Cliente devolveu um item comprado
    CANCELAMENTO_DE_VENDA,  // Venda estornada no caixa (Item retorna à prateleira)
    AJUSTE_SOBRA,           // Inventário: contagem física maior que sistema
    ESTOQUE_INICIAL,        // Implantação do sistema / Carga inicial
    AJUSTE_ENTRADA,         // Correção manual positiva genérica

    // --- SAÍDAS (Diminuem o Estoque) ---
    VENDA,                  // Saída padrão via PDV
    DEVOLUCAO_AO_FORNECEDOR,// Devolução de item defeituoso/vencido ao fabricante
    AJUSTE_PERDA,           // Inventário: item sumiu ou foi roubado
    AJUSTE_AVARIA,          // Inventário: item quebrado/inutilizado
    USO_INTERNO,            // Consumo próprio (ex: material de limpeza, tester)
    AJUSTE_SAIDA            // Correção manual negativa genérica
}
package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

/**
 * Define o motivo operacional da movimentação.
 * Organizado por efeito no saldo de estoque (Entrada vs Saída).
 */
public enum MotivoMovimentacaoDeEstoque {

    // --- ENTRADAS (Aumentam o Estoque) ---
    COMPRA_SEM_NOTA_FISCAL,
    COMPRA_COM_NOTA_FISCAL,      // Chegada de mercadoria (Nota de Entrada)
    DEVOLUCAO_CLIENTE,      // Cliente devolveu um item comprado
    CANCELAMENTO_DE_VENDA,  // Venda estornada no caixa
    AJUSTE_SOBRA,           // Inventário: contagem física maior que sistema
    ESTOQUE_INICIAL,        // Implantação do sistema / Carga inicial
    AJUSTE_ENTRADA,         // Correção manual positiva genérica
    AJUSTE_INVENTARIO_ENTRADA, // Adicionado para compatibilidade com legados

    // --- SAÍDAS (Diminuem o Estoque) ---
    VENDA,                  // Saída padrão via PDV
    DEVOLUCAO_AO_FORNECEDOR,// Devolução de item defeituoso ao fabricante
    AJUSTE_PERDA,           // Inventário: item sumiu ou foi roubado
    PERDA_OU_QUEBRA, // Adicionado para compatibilidade
    AJUSTE_AVARIA,          // Inventário: item quebrado/inutilizado
    USO_INTERNO,            // Consumo próprio (tester, limpeza)
    AJUSTE_SAIDA,           // Correção manual negativa genérica
    AJUSTE_INVENTARIO_SAIDA; // Adicionado para compatibilidade

    /**
     * Verifica se o motivo representa uma ENTRADA de mercadoria.
     */
    public boolean isEntrada() {
        return this == COMPRA_COM_NOTA_FISCAL || this == DEVOLUCAO_CLIENTE ||
                this == CANCELAMENTO_DE_VENDA || this == AJUSTE_SOBRA ||
                this == ESTOQUE_INICIAL || this == AJUSTE_ENTRADA ||
                this == AJUSTE_INVENTARIO_ENTRADA;
    }

    public boolean isSaida() {
        return !isEntrada();
    }
}
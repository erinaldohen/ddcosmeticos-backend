-- Índices para acelerar o Dashboard e Relatórios de Vendas
CREATE INDEX IF NOT EXISTS idx_venda_data ON tb_venda (data_venda);
CREATE INDEX IF NOT EXISTS idx_venda_status ON tb_venda (status_nfce);
CREATE INDEX IF NOT EXISTS idx_venda_cliente ON tb_venda (id_cliente);

-- Índices para busca rápida no PDV (Debounce)
CREATE INDEX IF NOT EXISTS idx_produto_descricao ON tb_produto (descricao);
CREATE INDEX IF NOT EXISTS idx_produto_barras ON tb_produto (codigo_barras);

-- Índices para o Financeiro (Contas a Receber/Pagar)
CREATE INDEX IF NOT EXISTS idx_conta_receber_vencimento ON conta_receber (data_vencimento);
CREATE INDEX IF NOT EXISTS idx_conta_receber_status ON conta_receber (status);
CREATE INDEX IF NOT EXISTS idx_conta_pagar_vencimento ON conta_pagar (data_vencimento);
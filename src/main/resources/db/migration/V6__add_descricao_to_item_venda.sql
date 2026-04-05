-- Adiciona a coluna (permitindo nulos temporariamente para não quebrar os registros antigos)
ALTER TABLE tb_item_venda ADD COLUMN descricao_produto VARCHAR(255);

-- Preenche as vendas antigas com o nome do produto atual (CORRIGIDO PARA A TABELA 'produto')
UPDATE tb_item_venda iv
SET descricao_produto = (SELECT p.descricao FROM produto p WHERE p.id = iv.produto_id)
WHERE iv.descricao_produto IS NULL AND iv.produto_id IS NOT NULL;

-- Coloca um nome padrão para itens antigos que por acaso não tinham produto vinculado
UPDATE tb_item_venda
SET descricao_produto = 'Produto Desconhecido'
WHERE descricao_produto IS NULL;

-- Agora que todos os registros antigos têm um nome, podemos forçar a coluna a ser NOT NULL (Blindagem)
ALTER TABLE tb_item_venda ALTER COLUMN descricao_produto SET NOT NULL;
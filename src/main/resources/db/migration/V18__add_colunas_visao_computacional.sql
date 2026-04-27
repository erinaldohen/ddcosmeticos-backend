-- Adiciona as colunas necessárias para o Motor de Visão Computacional MVC-F

ALTER TABLE produto
    ADD COLUMN hash_imagem VARCHAR(64),
ADD COLUMN revisao_imagem_pendente BOOLEAN DEFAULT FALSE;

-- Atualiza a auditoria do Envers caso esteja mapeado
ALTER TABLE produto_aud
    ADD COLUMN hash_imagem VARCHAR(64),
ADD COLUMN revisao_imagem_pendente BOOLEAN DEFAULT FALSE;
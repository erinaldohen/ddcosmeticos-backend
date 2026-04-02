-- 1. Atualização da tabela tb_item_venda e a sua tabela de auditoria
ALTER TABLE tb_item_venda ADD COLUMN codigo_barras VARCHAR(20);
ALTER TABLE tb_item_venda ADD COLUMN ncm VARCHAR(10);
ALTER TABLE tb_item_venda ADD COLUMN aliquota_icms NUMERIC(5,2);

ALTER TABLE tb_item_venda_aud ADD COLUMN codigo_barras VARCHAR(20);
ALTER TABLE tb_item_venda_aud ADD COLUMN ncm VARCHAR(10);
ALTER TABLE tb_item_venda_aud ADD COLUMN aliquota_icms NUMERIC(5,2);

-- 2. Atualização da tabela tb_venda e a sua tabela de auditoria
ALTER TABLE tb_venda ADD COLUMN numero_nfce BIGINT;
ALTER TABLE tb_venda ADD COLUMN serie_nfce INTEGER;
ALTER TABLE tb_venda RENAME COLUMN protocolo_autorizacao TO protocolo;

ALTER TABLE tb_venda_aud ADD COLUMN numero_nfce BIGINT;
ALTER TABLE tb_venda_aud ADD COLUMN serie_nfce INTEGER;
ALTER TABLE tb_venda_aud RENAME COLUMN protocolo_autorizacao TO protocolo;
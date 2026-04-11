-- Atualiza a tabela principal de clientes
ALTER TABLE cliente ADD COLUMN cep VARCHAR(10);
ALTER TABLE cliente ADD COLUMN logradouro VARCHAR(150);
ALTER TABLE cliente ADD COLUMN numero VARCHAR(20);
ALTER TABLE cliente ADD COLUMN complemento VARCHAR(100);
ALTER TABLE cliente ADD COLUMN bairro VARCHAR(100);
ALTER TABLE cliente ADD COLUMN cidade VARCHAR(100);
ALTER TABLE cliente ADD COLUMN uf VARCHAR(2);
ALTER TABLE cliente DROP COLUMN IF EXISTS endereco;

-- Atualiza a tabela de auditoria (pois o Cliente usa @Audited)
ALTER TABLE cliente_aud ADD COLUMN cep VARCHAR(10);
ALTER TABLE cliente_aud ADD COLUMN logradouro VARCHAR(150);
ALTER TABLE cliente_aud ADD COLUMN numero VARCHAR(20);
ALTER TABLE cliente_aud ADD COLUMN complemento VARCHAR(100);
ALTER TABLE cliente_aud ADD COLUMN bairro VARCHAR(100);
ALTER TABLE cliente_aud ADD COLUMN cidade VARCHAR(100);
ALTER TABLE cliente_aud ADD COLUMN uf VARCHAR(2);
ALTER TABLE cliente_aud DROP COLUMN IF EXISTS endereco;
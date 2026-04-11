-- Remove a obrigatoriedade (NOT NULL) da coluna documento na tabela cliente
ALTER TABLE cliente ALTER COLUMN documento DROP NOT NULL;
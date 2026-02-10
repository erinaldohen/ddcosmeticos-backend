-- Arquivo: src/main/resources/data.sql

-- Usamos MERGE INTO para que o H2 atualize se existir ou insira se não existir (evita duplicidade)
MERGE INTO fornecedor (cnpj_cpf, ativo, email, nome_fantasia, razao_social, telefone, tipo_pessoa)
    KEY(cnpj_cpf)
    VALUES ('11111111000111', true, 'financas@teste.com', 'Fornecedor Financeiro', 'Razao Financeira LTDA', '1166666666', 'JURIDICA');

MERGE INTO fornecedor (cnpj_cpf, ativo, email, nome_fantasia, razao_social, telefone, tipo_pessoa)
    KEY(cnpj_cpf)
    VALUES ('22222222000122', true, 'cartao@teste.com', 'Fornecedor Cartao', 'Razao Cartão LTDA', '1155555555', 'JURIDICA');

MERGE INTO fornecedor (cnpj_cpf, ativo, email, nome_fantasia, razao_social, telefone, tipo_pessoa)
    KEY(cnpj_cpf)
    VALUES ('99999999000199', true, 'preco@teste.com', 'Fornecedor Preço', 'Razao Preço LTDA', '1188888888', 'JURIDICA');

MERGE INTO fornecedor (cnpj_cpf, ativo, email, nome_fantasia, razao_social, telefone, tipo_pessoa)
    KEY(cnpj_cpf)
    VALUES ('12345678900', true, 'fisica@teste.com', 'Fornecedor PF', 'Nome Sobrenome', '1177777777', 'FISICA');

MERGE INTO fornecedor (cnpj_cpf, ativo, email, nome_fantasia, razao_social, telefone, tipo_pessoa)
    KEY(cnpj_cpf)
    VALUES ('12345678000199', true, 'juridica@teste.com', 'Fornecedor PJ', 'Razao PJ LTDA', '1177777777', 'JURIDICA');

MERGE INTO fornecedor (cnpj_cpf, ativo, email, nome_fantasia, razao_social, telefone, tipo_pessoa)
    KEY(cnpj_cpf)
    VALUES ('00000000000100', true, 'estoque@teste.com', 'Fornecedor Estoque', 'Razao Estoque', '1144444444', 'JURIDICA');

    -- ... (mantenha os inserts de fornecedor que já existem) ...

    -- INSERÇÃO DE USUÁRIO PADRÃO (Senha: 123456)
    -- A senha hash abaixo '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy' corresponde a '123456'
    MERGE INTO usuario (email, matricula, nome, senha, perfil_do_usuario, ativo)
        KEY(email)
        VALUES ('admin@admin.com', 'ADMIN', 'Administrador Sistema', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ROLE_ADMIN', true);
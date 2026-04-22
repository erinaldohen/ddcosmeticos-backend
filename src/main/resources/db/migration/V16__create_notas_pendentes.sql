CREATE TABLE nota_pendente_importacao (
                                          id SERIAL PRIMARY KEY,
                                          chave_acesso VARCHAR(44) UNIQUE NOT NULL,
                                          nsu VARCHAR(20) NOT NULL,
                                          cnpj_fornecedor VARCHAR(14),
                                          nome_fornecedor VARCHAR(255),
                                          valor_total NUMERIC(10, 2),
                                          xml_completo TEXT,
                                          status VARCHAR(50) DEFAULT 'PENDENTE_MANIFESTACAO', -- PENDENTE_MANIFESTACAO, PRONTO_IMPORTACAO, IMPORTADO
                                          data_emissao TIMESTAMP,
                                          data_captura TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabela simples para guardar o Último NSU pesquisado
CREATE TABLE controle_sefaz_nsu (
                                    id SERIAL PRIMARY KEY,
                                    cnpj_empresa VARCHAR(14) UNIQUE NOT NULL,
                                    ultimo_nsu VARCHAR(20) NOT NULL DEFAULT '0'
);
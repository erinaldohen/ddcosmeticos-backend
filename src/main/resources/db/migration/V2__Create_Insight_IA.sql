CREATE TABLE tb_insight_ia (
                               id BIGSERIAL PRIMARY KEY,
                               tipo VARCHAR(50) NOT NULL,
                               criticidade VARCHAR(20) NOT NULL,
                               titulo VARCHAR(255) NOT NULL,
                               mensagem TEXT NOT NULL,
                               acao_sugerida TEXT,
                               data_geracao TIMESTAMP NOT NULL,
                               resolvido BOOLEAN DEFAULT FALSE
);
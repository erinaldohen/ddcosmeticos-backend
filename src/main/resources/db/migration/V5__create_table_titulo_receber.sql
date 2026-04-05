CREATE TABLE tb_titulo_receber (
                                   id BIGSERIAL PRIMARY KEY,
                                   cliente_id BIGINT NOT NULL,
                                   venda_id BIGINT,
                                   descricao VARCHAR(255),
                                   data_compra DATE NOT NULL,
                                   data_vencimento DATE NOT NULL,
                                   data_pagamento DATE,
                                   valor_total DECIMAL(10, 2) NOT NULL,
                                   valor_pago DECIMAL(10, 2) DEFAULT 0,
                                   saldo_devedor DECIMAL(10, 2) NOT NULL,
                                   status VARCHAR(20) NOT NULL,

                                   CONSTRAINT fk_titulo_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id)
);

-- Index para performance na busca de devedores
CREATE INDEX idx_titulo_status ON tb_titulo_receber(status);
CREATE INDEX idx_titulo_cliente ON tb_titulo_receber(cliente_id);
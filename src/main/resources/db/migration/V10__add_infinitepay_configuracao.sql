ALTER TABLE tb_configuracao ADD COLUMN gateway_pagamento VARCHAR(50) DEFAULT 'MANUAL';
ALTER TABLE tb_configuracao ADD COLUMN infinitepay_client_id VARCHAR(255);
ALTER TABLE tb_configuracao ADD COLUMN infinitepay_client_secret VARCHAR(255);
ALTER TABLE tb_configuracao ADD COLUMN infinitepay_wallet_id VARCHAR(255);
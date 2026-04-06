ALTER TABLE tb_configuracao ADD COLUMN smtp_host VARCHAR(100);
ALTER TABLE tb_configuracao ADD COLUMN smtp_port INTEGER;
ALTER TABLE tb_configuracao ADD COLUMN smtp_username VARCHAR(100);
ALTER TABLE tb_configuracao ADD COLUMN smtp_password VARCHAR(100);
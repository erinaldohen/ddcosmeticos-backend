-- Adicionando os campos de TEF na tabela oficial
ALTER TABLE tb_pagamento_venda ADD COLUMN codigo_autorizacao VARCHAR(100);
ALTER TABLE tb_pagamento_venda ADD COLUMN cnpj_credenciadora VARCHAR(14);
ALTER TABLE tb_pagamento_venda ADD COLUMN bandeira_cartao VARCHAR(20);

-- Adicionando os campos de TEF na tabela de auditoria do Hibernate Envers
ALTER TABLE tb_pagamento_venda_aud ADD COLUMN codigo_autorizacao VARCHAR(100);
ALTER TABLE tb_pagamento_venda_aud ADD COLUMN cnpj_credenciadora VARCHAR(14);
ALTER TABLE tb_pagamento_venda_aud ADD COLUMN bandeira_cartao VARCHAR(20);
-- --- CARGA TABELA IBPT (Cosméticos e Perfumaria) ---
-- Valores aproximados vigentes (Exemplo 2024/2025)

-- 33030010: Perfumes
INSERT INTO tb_ibpt (codigo, descricao, nacional, importado, estadual, municipal, versao)
VALUES ('33030010', 'Perfumes', 13.45, 23.15, 18.00, 0.00, '24.1.A')
ON CONFLICT (codigo) DO NOTHING;

-- 33030020: Águas de Colônia
INSERT INTO tb_ibpt (codigo, descricao, nacional, importado, estadual, municipal, versao)
VALUES ('33030020', 'Aguas de colonia', 13.45, 23.15, 18.00, 0.00, '24.1.A')
ON CONFLICT (codigo) DO NOTHING;

-- 33041000: Batons / Maquiagem Lábios
INSERT INTO tb_ibpt (codigo, descricao, nacional, importado, estadual, municipal, versao)
VALUES ('33041000', 'Maquiagem labios', 14.15, 25.40, 18.00, 0.00, '24.1.A')
ON CONFLICT (codigo) DO NOTHING;

-- 33042010: Sombra, Delineador
INSERT INTO tb_ibpt (codigo, descricao, nacional, importado, estadual, municipal, versao)
VALUES ('33042010', 'Sombra/Delineador', 14.15, 25.40, 18.00, 0.00, '24.1.A')
ON CONFLICT (codigo) DO NOTHING;

-- 33043000: Esmaltes
INSERT INTO tb_ibpt (codigo, descricao, nacional, importado, estadual, municipal, versao)
VALUES ('33043000', 'Esmaltes', 14.15, 25.40, 18.00, 0.00, '24.1.A')
ON CONFLICT (codigo) DO NOTHING;

-- 33049910: Cremes, Loções
INSERT INTO tb_ibpt (codigo, descricao, nacional, importado, estadual, municipal, versao)
VALUES ('33049910', 'Cremes/Locoes', 14.15, 25.40, 18.00, 0.00, '24.1.A')
ON CONFLICT (codigo) DO NOTHING;

-- 33051000: Xampus
INSERT INTO tb_ibpt (codigo, descricao, nacional, importado, estadual, municipal, versao)
VALUES ('33051000', 'Xampus', 12.00, 20.50, 18.00, 0.00, '24.1.A')
ON CONFLICT (codigo) DO NOTHING;

-- 33059000: Condicionador/Máscara
INSERT INTO tb_ibpt (codigo, descricao, nacional, importado, estadual, municipal, versao)
VALUES ('33059000', 'Condicionador', 12.00, 20.50, 18.00, 0.00, '24.1.A')
ON CONFLICT (codigo) DO NOTHING;

-- 33072010: Desodorantes
INSERT INTO tb_ibpt (codigo, descricao, nacional, importado, estadual, municipal, versao)
VALUES ('33072010', 'Desodorantes', 13.00, 21.00, 18.00, 0.00, '24.1.A')
ON CONFLICT (codigo) DO NOTHING;
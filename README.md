Aqui está o arquivo **README.md** completo e profissional, documentando toda a arquitetura, funcionalidades e configurações de segurança que implementamos no projeto.

Você pode salvar este conteúdo em um arquivo chamado `README.md` na raiz do seu projeto.

-----

# 💄 DD Cosméticos - Sistema de Gestão (Backend)

Sistema de gestão comercial (ERP) e Ponto de Venda (PDV) desenvolvido para alta performance, segurança e conformidade fiscal. O projeto foca no controle rigoroso de estoque, cálculo real de lucratividade (PMP) e emissão de notas fiscais (NFC-e).

## 🚀 Tecnologias Utilizadas

O projeto foi construído utilizando as versões mais recentes do ecossistema Java ("Bleeding Edge"):

* **Java 25** (JDK)
* **Spring Boot 4.0.0**
* **Spring Security 7** (Autenticação Stateless com JWT)
* **Spring Data JPA** (Hibernate 7 com Dialeto MySQL)
* **MySQL 5.6/8.0** (Banco de dados em Produção - Hospedagem Uhserver)
* **Maven** (Gerenciamento de dependências)
* **Assinatura Digital A1** (Criptografia RSA para documentos fiscais)

-----

## 🛡️ Segurança e Arquitetura

O sistema foi blindado seguindo as melhores práticas de DevSecOps:

* **Autenticação JWT:** Tokens assinados com algoritmo HMAC256.
* **Tipagem Forte:** Perfis de acesso controlados via Enum (`ROLE_GERENTE`, `ROLE_CAIXA`) para evitar erros de consistência.
* **Proteção de Rotas:**
    * CORS restrito a origens confiáveis.
    * CSRF desativado (padrão para APIs REST).
    * Bloqueio total de rotas administrativas (Backdoors removidos).
* **Tratamento de Erros:** `GlobalExceptionHandler` implementado para retornar JSONs limpos e seguros, sem expor Stack Traces.
* **Performance:**
    * `open-in-view=false`: Previne travamento do pool de conexões.
    * `ddl-auto=validate`: Garante integridade do banco em produção.

-----

## 📦 Funcionalidades Principais

### 1\. Gestão de Vendas (PDV)

* Registro de venda com múltiplos itens.
* Baixa automática de estoque.
* Cálculo de descontos e totais.
* **Auditoria:** Vínculo do operador responsável pela venda.

### 2\. Fiscal (NFC-e)

* Geração de XML no padrão SEFAZ (Nota Fiscal de Consumidor).
* **Assinatura Digital:** Utiliza certificado A1 (`.pfx`) carregado no sistema.
* **Persistência Legal:** O XML assinado é armazenado no banco de dados para fins de fiscalização (Compliance).

### 3\. Inteligência Financeira

* **Entrada de Notas:** Registro de compras de fornecedores.
* **Cálculo de PMP:** O sistema recalcula automaticamente o *Preço Médio Ponderado* a cada entrada.
* **Lucro Real:** O relatório de vendas utiliza o custo do momento da venda (Snapshot) para calcular a margem de contribuição exata.

### 4\. Relatórios Gerenciais

* **Curva ABC (Pareto):** Classificação automática de produtos (Classe A, B, C) baseada na representatividade do faturamento.
* **Relatório Diário:** Visão consolidada de Faturamento Bruto, Líquido, CMV (Custo) e Lucro Líquido.

-----

## ⚙️ Configuração e Instalação

### Pré-requisitos

* Java 25 instalado.
* Banco de Dados MySQL criado.
* Arquivo de Certificado Digital (`.pfx`) na pasta de recursos (se for emitir notas reais).

### Variáveis de Ambiente (Obrigatório)

Por segurança, **nenhuma senha** está hardcoded no projeto. Para rodar a aplicação, você deve configurar as seguintes variáveis no sistema operacional ou no container:

| Variável | Descrição | Exemplo |
| :--- | :--- | :--- |
| `DB_HOST` | Endereço do Servidor MySQL | `mysql.seudominio.com.br` |
| `DB_NAME` | Nome do Banco de Dados | `ddcosmetic` |
| `DB_USER` | Usuário do Banco | `app_user` |
| `DB_PASSWORD` | Senha do Banco | `S3nhaF0rt3!` |
| `JWT_SECRET` | Chave para assinar Tokens | `Chave_Secreta_Aleatoria_e_Longa` |
| `CERT_PASS` | Senha do Certificado A1 | `123456` |

### Como Rodar em Produção

1.  **Gerar o Executável (.jar):**

    ```bash
    mvn clean package -DskipTests
    ```

2.  **Executar o Sistema:**

    ```bash
    # Exemplo no Windows (CMD)
    set DB_HOST=ddcosmetic.mysql.uhserver.com
    set DB_NAME=ddcosmetic
    set DB_USER=usuario
    set DB_PASSWORD=senha
    set JWT_SECRET=segredo123
    set CERT_PASS=123456

    java -jar target/ddcosmeticos-backend-0.0.1-SNAPSHOT.jar
    ```

-----

## 📚 Documentação da API (Endpoints)

Como o Swagger não é compatível com Spring Boot 4.0.0 no momento, utilize a **Coleção do Postman** exportada ou siga a referência abaixo.

### 🔐 Autenticação

* **Login:** `POST /api/v1/auth/login`
    * *Body:* `{ "matricula": "GERENTE02", "senha": "..." }`
    * *Retorno:* Token JWT e dados do usuário.

### 🛒 Vendas

* **Registrar Venda:** `POST /api/v1/vendas`
    * *Auth:* Bearer Token (Caixa/Gerente)
    * *Body:* Lista de itens e descontos.

### 🧾 Fiscal

* **Gerar/Consultar NFC-e:** `GET /api/v1/fiscal/nfce/{idVenda}`
    * *Auth:* Bearer Token
    * *Retorno:* JSON contendo o XML assinado e status da SEFAZ.

### 📦 Estoque & Custos

* **Entrada de Nota:** `POST /api/v1/custo/entrada`
    * *Auth:* Bearer Token (Apenas Gerente)
    * *Efeito:* Aumenta estoque e recalcula PMP.

### 📊 Relatórios

* **Curva ABC:** `GET /api/v1/relatorios/curva-abc`
* **Lucro Diário:** `GET /api/v1/relatorios/diario`
    * *Auth:* Bearer Token (Apenas Gerente)

-----

## 👤 Credenciais Iniciais

Para o primeiro acesso em um banco de dados recém-criado (após rodar o script SQL de carga):

* **Matrícula:** `GERENTE02`
* **Senha:** `123456`

-----

## 📝 Licença

Este software é proprietário e desenvolvido exclusivamente para **DD Cosméticos**.
Proibida a cópia ou redistribuição não autorizada.
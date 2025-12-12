# 💄 DD Cosméticos - Sistema de Gestão (Backend)

Sistema de gestão comercial (ERP) e Ponto de Venda (PDV) desenvolvido para alta performance, segurança e conformidade fiscal. O projeto suporta operações complexas como vendas híbridas (fiscal/não-fiscal), auditoria de estoque negativo e emissão inteligente de NFC-e.

## 🚀 Tecnologias Utilizadas

* **Java 25** (JDK)
* **Spring Boot 4.0.0**
* **Spring Security 7** (Autenticação Stateless com JWT)
* **Spring Data JPA** (Hibernate 7 com Dialeto MySQL)
* **MySQL 8.0** (Banco de Dados de Produção)
* **Maven** (Gerenciamento de Dependências)

---

## 🛡️ Segurança e Arquitetura

O sistema foi blindado seguindo as melhores práticas de DevSecOps:

* **Autenticação JWT:** Tokens assinados com algoritmo HMAC256.
* **Tipagem Forte:** Perfis de acesso controlados via Enum (`ROLE_GERENTE`, `ROLE_CAIXA`).
* **Proteção de Rotas:**
  * CORS restrito a origens confiáveis (Front-end autorizado).
  * CSRF desativado (Padrão para APIs REST).
  * **Zero Backdoors:** Rotas de administração removidas do código final.
* **Tratamento de Erros:** `GlobalExceptionHandler` retorna JSONs limpos, ocultando Stack Traces.
* **Banco de Dados:** Configurado com `ddl-auto=update` para evolução ágil e `open-in-view=false` para performance.

---

## 📦 Regras de Negócio Avançadas

### 1. Venda Híbrida Inteligente
O sistema permite que o operador registre, em uma única venda, produtos com diferentes origens fiscais.
* **No Balcão:** O cliente leva tudo o que comprou.
* **No Fiscal (NFC-e):** O sistema filtra automaticamente os itens. Apenas produtos com a flag `possui_nf_entrada = true` são incluídos no XML enviado à SEFAZ. Itens sem origem fiscal são registrados internamente mas ocultados do documento fiscal.

### 2. Gestão de Estoque e Auditoria
* **Estoque Negativo:** A venda **não é bloqueada** por falta de estoque físico (evita atrito com cliente).
* **Auditoria Automática:** Se o estoque ficar negativo, o sistema:
  1. Grava um registro indelével na tabela `auditoria`.
  2. Envia um alerta no JSON de resposta para o Caixa/Gerente.
  3. Marca o status fiscal da venda como `PENDENTE_ANALISE_GERENTE`.

### 3. Soft Delete (Imutabilidade)
Nenhum dado crítico (Produto, Usuário, Venda) é excluído fisicamente do banco. O sistema utiliza exclusão lógica (`ativo = false`) para manter o histórico e integridade referencial.

---

## ⚙️ Configuração e Instalação

### Variáveis de Ambiente (Obrigatório)
Configure estas variáveis no servidor para rodar a aplicação:

| Variável | Descrição | Exemplo |
| :--- | :--- | :--- |
| `DB_HOST` | Endereço do MySQL | `ddcosmetic.mysql.uhserver.com` |
| `DB_NAME` | Nome do Banco | `ddcosmetic` |
| `DB_USER` | Usuário do Banco | `app_user` |
| `DB_PASSWORD` | Senha do Banco | `S3nhaF0rt3!` |
| `JWT_SECRET` | Chave do Token | `Chave_Secreta_Aleatoria` |
| `CERT_PASS` | Senha do Certificado A1 | `123456` |

### Como Rodar em Produção

1.  **Gerar o Executável (.jar):**
    ```bash
    mvn clean package -DskipTests
    ```

2.  **Executar o Sistema:**
    ```bash
    java -jar target/ddcosmeticos-backend-0.0.1-SNAPSHOT.jar
    ```

---

## 📚 Documentação da API (Principais Endpoints)

### 🔐 Autenticação
* **Login:** `POST /api/v1/auth/login`
  * *Retorno:* Token JWT e Perfil.

### 🛒 Vendas (PDV)
* **Registrar Venda:** `POST /api/v1/vendas`
  * *Auth:* Bearer Token
  * *Comportamento:* Aceita itens mistos. Retorna alertas de estoque e status fiscal.

### 🧾 Fiscal
* **Gerar NFC-e:** `GET /api/v1/fiscal/nfce/{idVenda}`
  * *Auth:* Bearer Token
  * *Lógica:* Gera XML assinado contendo **apenas** os itens fiscais da venda selecionada.

### 📊 Relatórios
* **Curva ABC:** `GET /api/v1/relatorios/curva-abc` (Classificação Pareto A/B/C)
* **Lucro Diário:** `GET /api/v1/relatorios/diario`

---

## 👤 Credenciais Iniciais

* **Matrícula:** `GERENTE02`
* **Senha Inicial:** `123456` (Deve ser alterada após o primeiro acesso)

---

## 📝 Licença
Software proprietário desenvolvido exclusivamente para **DD Cosméticos**.
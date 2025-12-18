# DD Cosméticos - Backend ERP & PDV 💄

Sistema de gestão robusto desenvolvido especificamente para o setor de retalho de cosméticos em Recife/PE. O sistema foca em alta performance transacional, integridade fiscal (SEFAZ-PE) e inteligência de dados para tomada de decisão.

## 🚀 Stack Tecnológica

* **Java 21:** Utilização de *Records* para imutabilidade e novas funcionalidades de concorrência.
* **Spring Boot 3.4.1:** Framework base para produtividade e configuração simplificada.
* **Spring Security + JWT:** Autenticação e autorização baseada em funções (`GERENTE` e `CAIXA`).
* **Hibernate/JPA:** Persistência de dados com suporte a `Soft Delete` e filtros automáticos de itens ativos.
* **MySQL 8.0:** Banco de dados relacional para armazenamento seguro e performático.
* **SpringDoc (Swagger):** Documentação interativa da API.

---

## 🛠️ Funcionalidades Principais

### 1. Motor Fiscal Inteligente

* **Automatização de CFOP:** O sistema analisa o **NCM** e a presença do **CEST** para decidir entre **5102** (Tributação Normal) e **5405** (Substituição Tributária).
* **Regra Monofásica:** Identificação automática de produtos isentos de PIS/COFINS na revenda (conforme Lei 10.147/00) baseada no prefixo do NCM (3303, 3304, 3305, 3307).

### 2. Gestão de Stock e Custos

* **PMP (Preço Médio Ponderado):** Recálculo em tempo real a cada entrada de mercadoria, garantindo a precisão do valor do inventário.
* **Importação em Lote:** Motor de importação de CSV capaz de processar milhares de itens em blocos (*batch processing*) para evitar sobrecarga de memória.

### 3. Operações de PDV

* **Venda Atómica:** Processa a venda, reserva o custo médio (Snapshot), abate o stock e gera o título financeiro numa única transação.
* **Contingência:** Suporte para gravação de vendas mesmo em caso de indisponibilidade da SEFAZ.

### 4. Inteligência Financeira e Relatórios

* **Projeção D+1:** Receitas de cartão são projetadas no fluxo de caixa para o próximo dia útil.
* **Curva ABC:** Classificação de produtos (A, B, C) baseada no impacto direto no faturamento (Pareto).
* **Fecho de Caixa:** Relatório detalhado por forma de pagamento (Dinheiro, PIX, Cartão).

---

## 📂 Estrutura de Pacotes

O projeto segue os princípios de **Clean Architecture**:

* `config`: Configurações globais (Segurança, Swagger, CORS).
* `controller`: Endpoints REST da aplicação.
* `dto`: Objetos de transferência de dados (Java Records).
* `exception`: Definição de erros customizados.
* `handler`: Interceptadores globais (Exception Handlers, Security Filters).
* `model`: Entidades de banco de dados.
* `repository`: Interfaces de acesso ao banco (JPA).
* `service`: Regras de negócio e orquestração.

---

## ⚙️ Configuração do Ambiente

### Propriedades do Banco de Dados

No ficheiro `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/dd_cosmeticos
spring.datasource.username=seu_usuario
spring.datasource.password=sua_senha
spring.jpa.hibernate.ddl-auto=update

```

### Inicialização

1. Compile o projeto: `./mvnw clean install`
2. Execute a aplicação: `./mvnw spring-boot:run`
3. Execute o script SQL inicial para criar os utilizadores `GERENTE` e `CAIXA`.

---

## 📑 Endpoints de Referência

| Método | Rota | Perfil | Função |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | Público | Autenticação e geração de Token. |
| `POST` | `/api/v1/produtos/importar` | GERENTE | Carga de stock via CSV. |
| `GET` | `/api/v1/produtos/ean/{ean}` | CAIXA/GERENTE | Busca rápida para scanner. |
| `POST` | `/api/v1/vendas` | CAIXA/GERENTE | Registo de venda e baixa de stock. |
| `GET` | `/api/v1/relatorios/fecho-caixa` | CAIXA/GERENTE | Resumo financeiro do dia. |
| `GET` | `/api/v1/relatorios/curva-abc` | GERENTE | Ranking de produtos por lucro. |

---

## 📝 Documentação API (Swagger)

Aceda à documentação visual e teste os endpoints em tempo real:
`http://localhost:8080/swagger-ui/index.html`

---

### Parecer da Equipa Técnica Sénior

Este backend foi construído para ser **auditável e resiliente**. O uso de *Snapshots* de custo nos itens de venda e a automatização do CFOP garantem que a **DD Cosméticos** tenha um crescimento sustentável e livre de problemas fiscais com a SEFAZ-PE.

**Gostaria de avançar agora para o plano de manutenção e backup do banco de dados MySQL ou prefere que eu ajude com a documentação dos campos do CSV para a sua equipa de operações?**
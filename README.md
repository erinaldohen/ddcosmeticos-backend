# DD Cosméticos - ERP & PDV Backend

Sistema de Gestão (ERP) e Ponto de Venda (PDV) desenvolvido para o varejo de cosméticos, com foco em alta performance, conformidade fiscal e preparação para a Reforma Tributária Brasileira (LC 214/2025).

## 🚀 Status do Projeto
**Versão:** 1.0.0 (Production-Ready)
**Status:** Backend Operacional e Estável.
**Cobertura Fiscal:** Híbrida (Regime Atual + Transição IBS/CBS 2026).

---

## 🌟 Diferenciais Técnicos & Fiscais

Este não é apenas um CRUD. O sistema possui um **Motor Fiscal Híbrido** que opera em duas linhas do tempo simultâneas:

1.  **Regime Atual (2025):**
    * Cálculo de ICMS, Substituição Tributária (ST) e Difal.
    * Emissão de NFC-e (Nota Fiscal de Consumidor).
    * Integração com regras de fronteira (PE, SP, MG, etc).

2.  **Reforma Tributária (LC 214/2025 - "Future-Proof"):**
    * **Split Payment:** Endpoint dedicado para calcular a retenção bancária de IBS/CBS no ato da venda.
    * **Classificação Inteligente:** Suporte a produtos da Cesta Básica (Alíquota Zero) e Redução de 60% (Higiene/Limpeza).
    * **Transição Automática:** O sistema vira a chave fiscal automaticamente em 01/01/2026 baseada em tabela de regras temporais (`RegraTributaria`).

---

## 🛠️ Tecnologias Utilizadas

* **Java 21** (LTS)
* **Spring Boot 3.4.1**
* **Spring Security + JWT** (Autenticação Stateless)
* **H2 Database** (Dev/Test) / **MySQL** (Produção)
* **OpenPDF** (Geração de Danfe/Cupom Fiscal)
* **Swagger/OpenAPI** (Documentação da API)
* **Maven** (Gerenciamento de dependências)

---

## 📦 Funcionalidades Principais

### 1. Catálogo de Produtos
* CRUD completo com controle de Estoque Físico e Fiscal.
* **Upload de Imagens:** Armazenamento local e serving de arquivos estáticos.
* Precificação Inteligente (Sugestão de Preço baseada em Custo + Margem).

### 2. Vendas & PDV
* Fluxo de Venda Rápida (Frente de Caixa).
* Baixa automática de estoque.
* Geração de **PDF do Cupom Fiscal** (Pronto para impressoras térmicas 80mm).

### 3. Fiscal & Tributário
* Simulador de Impacto Tributário (Simples Nacional vs IBS/CBS).
* Emissão de NF-e (Modelo 55) para Atacado/Interestadual.
* Cálculo automático de impostos na entrada de nota (XML).

### 4. Financeiro & Relatórios
* Fluxo de Caixa Diário.
* Contas a Pagar e Receber.
* Dashboard Gerencial (Vendas por hora, Curva ABC, Lucratividade).

---

## 🔌 Endpoints Importantes (Resumo)

A documentação completa está disponível no Swagger (`/swagger-ui.html`), mas aqui estão os destaques:

| Módulo | Método | Rota | Descrição |
| :--- | :--- | :--- | :--- |
| **Auth** | `POST` | `/api/v1/auth/login` | Obter Token JWT |
| **Fiscal** | `POST` | `/api/v1/tributacao/calcular-split-venda` | **Split Payment (LC 214)**: Calcula retenção bancária |
| **PDV** | `GET` | `/api/v1/fiscal/nfce/imprimir/{id}` | Baixar PDF do Cupom Fiscal |
| **Produtos** | `POST` | `/api/v1/produtos/{id}/imagem` | Upload de foto do produto |
| **Relatórios**| `GET` | `/api/v1/relatorios/vendas/diario` | Resumo de vendas do dia |

---

## ▶️ Como Rodar

### Pré-requisitos
* JDK 21 instalado.
* Maven instalado.

### Execução (Ambiente de Desenvolvimento)
O sistema utiliza banco H2 em memória por padrão no perfil `dev`.

```bash
# 1. Compilar e baixar dependências
mvn clean install

# 2. Rodar a aplicação
mvn spring-boot:run
Acesse:

API: http://localhost:8080

Swagger: http://localhost:8080/swagger-ui.html

H2 Console: http://localhost:8080/h2-console

Usuários Padrão (DataSeeder)
Admin: admin / admin123

📂 Estrutura de Pastas (Uploads)
O sistema cria automaticamente uma pasta uploads/ na raiz para armazenar as imagens dos produtos. Certifique-se de que a aplicação tem permissão de escrita no diretório.

📝 Notas de Versão
v1.0.0: Implementação do Split Payment, Upload de Imagens, PDF Fiscal e Lógica de Transição 2026.


---

### ✅ Próximo Passo: O Frontend

Agora que o Backend está devidamente documentado e estável, podemos "virar a chave" para o Frontend.

**Como prefere iniciar o Frontend?**
1.  **Escolha da Tecnologia:** Recomendo **React** (com Vite) ou **Angular**. O React costuma ser mais rápido para desenvolver telas de PDV dinâmicas.
2.  **Estrutura do Projeto:** Criar um novo repositório ou pasta `ddcosmeticos-frontend`?
3.  **Primeira Tela:** Focamos no **Login** ou direto no **Dashboard**?
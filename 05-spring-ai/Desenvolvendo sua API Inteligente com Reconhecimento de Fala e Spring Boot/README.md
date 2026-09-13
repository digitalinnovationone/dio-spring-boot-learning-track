# 🚀 API Inteligente de Orçamento Financeiro com Spring AI e Reconhecimento de Fala

> **Desafio DIO:** Desenvolvendo sua API Inteligente com Reconhecimento de Fala e Spring Boot.

Uma API REST desenvolvida em **Java 21** e **Spring Boot 4 / Spring AI**, capaz de receber comandos de voz, transcrever áudios em tempo real, identificar intenções financeiras utilizando IA com **Tool Calling (Function Calling)**, persistir dados transacionais e responder com síntese de áudio gerada por IA.

---

## 💡 O Que a Aplicação Faz

A aplicação atua como um assistente financeiro pessoal capaz de processar instruções em linguagem natural (tanto via texto quanto através de áudio gravado).

### 🔄 Fluxo Principal de Processamento:
1. **Envio de Áudio**: O cliente envia um arquivo de voz (`.mp3`, `.m4a`, etc.) via endpoint multipart.
2. **Speech-to-Text (Transcrição)**: A API utiliza o modelo Whisper (`whisper-1`) da OpenAI para transcrever o áudio em texto em português.
3. **Compreensão de Intenção & Tool Calling**: O Spring AI (`ChatClient` + `gpt-4o-mini`) interpreta o comando e aciona automaticamente os casos de uso de negócio:
   - Cadastrar novo gasto (`persist-transaction`);
   - Consultar gastos por categoria (`list-transactions-by-category`);
   - Obter métricas e resumo financeiro (`get-financial-summary`).
4. **Persistência / Consulta**: Os use cases interagem com a camada de domínio e banco de dados relacional.
5. **Text-to-Speech (Síntese de Voz)**: A resposta em texto da IA é sintetizada em áudio MP3 (`gpt-4o-mini-tts` / voz `nova`) e devolvida ao cliente para escuta imediata.

---

## 🛠️ Tecnologias Utilizadas

- **Java 21** (LTS)
- **Spring Boot 4.0.5** & **Spring Web**
- **Spring AI 2.0.0-M4** (OpenAI Starter: Whisper, GPT-4o-mini, TTS)
- **Spring Data JPA & Hibernate**
- **MySQL / H2 Database**
- **Lombok**
- **JUnit 5 & Mockito** (Testes Unitários)
- **Docker Compose**

---

## ✨ Melhorias e Evoluções Implementadas no Desafio

Neste projeto, foram implementadas melhorias práticas além do projeto base:

1. **Novo Caso de Uso de Resumo Financeiro (`GetFinancialSummaryUseCase`)**:
   - Desenvolvido e registrado como ferramenta para a IA (`@Tool(name = "get-financial-summary")`).
   - Agora a IA consegue responder a perguntas em áudio como:
     - *"Quanto eu já gastei até agora?"*
     - *"Qual o meu gasto total e média de despesas?"*
     - *"Me dê um resumo financeiro dos meus gastos."*
2. **Novo Endpoint REST (`GET /transactions/summary`)**:
   - Permite consultar as métricas consolidadas (total gasto em R$, quantidade de transações, maior despesa, média de gastos e divisão por categoria) diretamente em formato JSON.
3. **Expansão de Categorias (`Category.java`)**:
   - Inclusão de novas categorias de despesas (`UTILITIES`, `ENTERTAINMENT`, `EDUCATION`, `FOOD`, `TRANSPORT`, `OTHER`) com mapeamento inteligente de contexto.
4. **Validações de Domínio (`PersistTransactionUseCase`)**:
   - Validações para impedir valores nulos, negativos ou iguais a zero, além de validação de descrição obrigatória e categoria padrão de fallback.
5. **Prompt de Sistema Especializado (`system-message.st`)**:
   - Ajuste das instruções de sistema para respostas mais concisas, amigáveis e estruturadas em Português do Brasil, otimizadas para reprodução em áudio.
6. **Suíte de Testes Unitários**:
   - Adicionados testes automatizados cobrindo os novos use cases e validações (`GetFinancialSummaryUseCaseTest` e `PersistTransactionUseCaseTest`).

---

## 📂 Arquitetura do Projeto (Clean Architecture / DDD)

```text
src/main/java/dio/budgeting/
├── application/                     # Casos de uso da aplicação (Use Cases e Tools)
│   ├── GetFinancialSummaryUseCase.java
│   ├── ListTransactionsByCategoryUseCase.java
│   ├── PersistTransactionUseCase.java
│   ├── input/
│   │   └── PersistTransactionInput.java
│   └── output/
│       ├── FinancialSummaryOutput.java
│       └── TransactionOutput.java
├── domain/                          # Entidades, Enums e Contratos de Repositório
│   ├── Category.java
│   ├── Transaction.java
│   ├── TransactionId.java
│   └── TransactionRepository.java
└── infrastructure/                  # Adaptadores HTTP (REST) e Persistência (JPA)
    ├── http/
    │   ├── TransactionController.java
    │   ├── request/
    │   └── response/
    └── persistence/
        ├── entity/
        └── repository/
```

---

## 🚀 Como Executar a Aplicação

### 1. Pré-requisitos
- JDK 17 ou JDK 21 instalado
- Chave de API da OpenAI (`OPENAI_API_KEY`)

### 2. Configurar Chave da OpenAI
No terminal (PowerShell / Bash / CMD):

```bash
# Windows (PowerShell)
$env:OPENAI_API_KEY="sua_chave_openai_aqui"

# Linux / Mac / Git Bash
export OPENAI_API_KEY="sua_chave_openai_aqui"
```

### 3. Rodar a Aplicação
```bash
./gradlew bootRun
```

### 4. Executar os Testes Unitários
```bash
./gradlew test --tests dio.budgeting.application.*
```

---

## 📡 Endpoints da API

| Método | Rota | Descrição |
| :--- | :--- | :--- |
| `POST` | `/transactions` | Cria uma transação via JSON |
| `GET` | `/transactions/{category}` | Lista transações filtradas por categoria |
| `GET` | `/transactions/summary` | **(Novo)** Retorna métricas financeiras consolidadas |
| `POST` | `/transactions/ai` | Recebe arquivo de voz (multipart/form-data) e devolve áudio MP3 |

### Exemplo de Chamada ao Endpoint de Resumo:
```bash
curl -X GET http://localhost:8080/transactions/summary
```
**Resposta JSON:**
```json
{
  "totalSpent": 250.0,
  "totalTransactions": 3,
  "averageSpent": 83.33,
  "maxSingleSpent": 150.0,
  "totalByCategory": {
    "GROCERIES": 200.0,
    "PHARMA": 50.0
  }
}
```

---

## 🎓 Aprendizados Obtidos no Desafio

- Integração nativa de modelos generativos e modelos de áudio com o ecossistema Spring via **Spring AI**;
- Uso do conceito de **Tool Calling (Function Calling)** com anotações `@Tool`, permitindo que LLMs invoquem métodos e serviços de negócio de forma segura;
- Separação de responsabilidades preservando princípios de **Clean Architecture** e **Domain-Driven Design (DDD)**;
- Pipeline completo de processamento de voz: **Voz -> Texto (Whisper) -> Raciocínio (GPT) -> Ação (JPA) -> Resposta (TTS)**.

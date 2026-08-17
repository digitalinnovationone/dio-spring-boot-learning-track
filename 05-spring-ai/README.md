# 🎙️ DIO Budgeting API - Spring AI & Arquitetura em Camadas

API de controle financeiro pessoal que utiliza Inteligência Artificial generativa e processamento multimodal (áudio) para registrar e consultar transações financeiras através de comandos de voz, preservando os princípios de **Domain-Driven Design (DDD)** e **Clean Architecture**.

---

## 📌 O que o Projeto Faz

A aplicação atua como um assistente financeiro inteligente por voz. Em vez de preencher formulários manualmente, o usuário envia um arquivo de áudio descrevendo um gasto ou fazendo uma consulta. A API:

1. **Transcreve o áudio (Speech-to-Text):** Converte a fala em texto em português via Whisper.
2. **Interpreta a intenção (Tool Calling):** O modelo GPT-4o-mini analisa o comando e decide autonomamente qual caso de uso acionar no backend (registrar gasto, consultar por categoria ou calcular total).
3. **Responde por voz (Text-to-Speech):** A resposta gerada é convertida em um áudio `.mp3` retornado ao usuário.

---

## 🛠️ Tecnologias Utilizadas

- **Java & Spring Boot:** Núcleo da aplicação e injeção de dependências.
- **Spring AI:**
  - `TranscriptionModel` (OpenAI Whisper) para transcrição de áudio.
  - `ChatClient` com suporte a **Tool Calling** (`@Tool` e `@ToolParam`) para orquestração de casos de uso.
  - `TextToSpeechModel` para síntese de áudio (TTS).
- **Spring Data JPA & Hibernate:** Persistência relacional de dados.
- **Lombok:** Produtividade e redução de boilerplate.
- **Gradle:** Gerenciamento de dependências e build.

---

## 🚀 Melhorias e Evoluções Implementadas

O projeto foi expandido com foco em regras de negócio mais realistas e novas habilidades para a IA:

### 1. Suporte a Novas Categorias Financeiras
- **Domínio:** Expansão do enum `Category` com `LEISURE` (lazer), `EDUCATION` (educação), `HEALTH` (saúde) e `HOUSING` (moradia).
- **Engenharia de Prompt:** Mapeamento de sinônimos no `system-message.st` para garantir precisão do LLM na categorização.

### 2. Diferenciação entre Receitas e Despesas
- **Domínio e Banco:** Criação do enum `TransactionType` (`EXPENSE` e `INCOME`), permitindo registrar tanto saídas quanto entradas de dinheiro.
- **Interpretação Semântica:** A IA identifica automaticamente o tipo da transação a partir dos verbos falados (*"gastei"*, *"paguei"* vs. *"recebi"*, *"salário"*).

### 3. Nova Ferramenta de IA: Consulta do Total de Gastos
- **Caso de Uso:** Criação do `GetTotalExpensesUseCase` anotado com `@Tool(name = "get-total-expenses")`, permitindo ao assistente responder perguntas sobre o total acumulado de despesas.
- **Endpoint REST:** Rota `GET /transactions/total` para consultas diretas via HTTP.

---

## ⚙️ Como Executar a Aplicação

### Pré-requisitos
- JDK instalado (compatível com a versão do projeto).
- Chave de API da OpenAI (`OPENAI_API_KEY`).

### Passos

1. **Definir a chave da OpenAI como variável de ambiente:**

   - No Linux/macOS:
     ```bash
     export OPENAI_API_KEY="sua_chave_aqui"
     ```
   - No Windows (PowerShell):
     ```powershell
     $env:OPENAI_API_KEY="sua_chave_aqui"
     ```

2. **Iniciar a aplicação via Gradle:**
   ```bash
   ./gradlew bootRun
   ```

---

## 🧪 Como Testar o Fluxo Principal

### 1. Testando o Fluxo de IA por Voz (`POST /transactions/ai`)

Envie um arquivo de áudio (ex: `audio.wav` ou `audio.mp3`) contendo uma frase como:
> *"Gastei 45 reais na farmácia comprando remédios."* ou  
> *"Qual o valor total de gastos que eu já tive até agora?"*

Exemplo com cURL:
```bash
curl -X POST http://localhost:8080/transactions/ai \
  -H "Content-Type: multipart/form-data" \
  -F "file=@/caminho/do/seu/audio.mp3" \
  --output resposta.mp3
```
*O arquivo `resposta.mp3` gerado conterá a resposta falada pelo assistente financeiro.*

### 2. Testando os Endpoints REST Tradicionais

- **Criar transação:**
  ```bash
  curl -X POST http://localhost:8080/transactions \
    -H "Content-Type: application/json" \
    -d '{"description": "Curso DIO", "category": "EDUCATION", "amount": 10000, "type": "EXPENSE"}'
  ```

- **Consultar total de gastos:**
  ```bash
  curl -X GET http://localhost:8080/transactions/total
  ```

- **Consultar por categoria:**
  ```bash
  curl -X GET http://localhost:8080/transactions/EDUCATION
  ```

---

## 💡 O que Aprendi Durante o Desafio

- **Integração do Spring AI:** Como configurar e utilizar modelos de chat, transcrição e síntese de voz de forma integrada no ecossistema Spring.
- **Tool Calling / Function Calling na prática:** Como expor Casos de Uso (`@Service`) como ferramentas (`@Tool`) que o LLM pode decidir invocar de forma autônoma e tipada.
- **Engenharia de Prompt para Sistemas Backend:** Como estruturar prompts de sistema (`.st`) com instruções claras de contexto, categorias e parâmetros para evitar alucinações.
- **Disciplina Arquitetural (DDD):** A importância de isolar o modelo de domínio e as regras de negócio dos detalhes de infraestrutura e provedores de IA.
   
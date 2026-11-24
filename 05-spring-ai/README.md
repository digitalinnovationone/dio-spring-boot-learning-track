# DIO Spring Boot - Projeto final 05: Spring AI (budgeting)

Entrega do desafio **"Desenvolvendo sua API Inteligente com Reconhecimento de Fala e Spring Boot"** da DIO.

## O que o projeto faz

É uma API de orçamento pessoal que entende comandos de voz. Você envia um áudio como
"gastei 80 reais no mercado" ou "quanto gastei este mês?" e a API:

1. transcreve o áudio para texto (Whisper);
2. usa um modelo de linguagem (gpt-4o-mini) para entender a intenção;
3. executa uma função real da aplicação por **tool calling**: registra ou consulta transações;
4. salva ou lê os dados no MySQL;
5. devolve a resposta falada em MP3 (text-to-speech) ou em JSON.

A IA nunca acessa o banco direto: ela só chama os casos de uso da camada `application`,
os mesmos que os endpoints REST usam.

## Tecnologias

- Java 25 e Spring Boot 4
- Spring AI 2.0 (OpenAI): `ChatClient`, `@Tool`, `TranscriptionModel`, `TextToSpeechModel`
- Spring Data JPA e MySQL 9 (Docker Compose)
- JUnit 5, AssertJ, Mockito, MockMvc e H2 nos testes
- Gradle e Lombok

## Melhorias implementadas

| Melhoria | O que mudou |
|---|---|
| **Correção do valor em reais** | A IA grava o valor em centavos (8000), mas a resposta devolvia `8000.00`. Agora devolve `80.00` (`Money.fromCents`). |
| **Data da transação** | Toda transação tem data. Se a pessoa disser "ontem" ou "dia 5", a IA calcula a data. Se não disser, usa a data de hoje. |
| **Novas categorias** | Além de `GROCERIES`, `PHARMA` e `AUTO`: `RESTAURANT`, `SHOPPING`, `LEISURE` e `OTHER`. |
| **Novas ferramentas de tool calling** | `get-total-by-category` (quanto gastei em uma categoria) e `get-spending-summary` (resumo de um período, com total por categoria). |
| **Validações antes de salvar** | O domínio rejeita valor menor ou igual a zero, descrição vazia ou longa demais, e categoria ou data ausentes. |
| **Erros claros** | `ApiExceptionHandler` devolve `ProblemDetail` (400 para dados inválidos e 502 quando a OpenAI falha). |
| **Arquitetura** | A orquestração da IA saiu do controller e foi para `BudgetAssistantUseCase`. O `ChatClient` é configurado em `AiConfig`. |
| **Prompt melhor** | O prompt de sistema recebe a data de hoje, explica centavos e categorias, e pede respostas curtas próprias para áudio. |
| **Endpoints para testar sem ouvir áudio** | `/transactions/ai/text` (áudio para JSON) e `/transactions/ai/chat` (texto para JSON). |
| **Testes** | Testes unitários do domínio, dos casos de uso e dos controllers, que rodam **sem chave da OpenAI e sem Docker**. |

## Estrutura

```
src/main/java/dio/budgeting
├── domain          Transaction, Category, regras de validação e contrato do repositório
├── application     casos de uso (também expostos como @Tool) e BudgetAssistantUseCase
└── infrastructure
    ├── config      AiConfig (ChatClient e ferramentas) e ClockConfig
    ├── http        TransactionController, AssistantController e ApiExceptionHandler
    └── persistence entidade e repositório JPA
```

## Como executar

Pré-requisitos: Java 25, Docker e uma chave da OpenAI.

```bash
export OPENAI_API_KEY="sua_chave_aqui"
```

```bash
./gradlew bootRun
```

O Docker Compose sobe o MySQL sozinho na porta 3307.

> Se você já tinha rodado a versão anterior, recrie o banco para pegar a nova coluna de data:
> `docker compose down -v`

## Como testar o fluxo principal

### 1. Fluxo completo por voz (áudio para áudio)

```bash
curl -F "file=@src/test/resources/audio/recording-1.m4a" \
  http://localhost:8080/transactions/ai --output resposta.mp3
```

### 2. Áudio para texto (ver a transcrição e a resposta)

```bash
curl -F "file=@src/test/resources/audio/recording-1.m4a" \
  http://localhost:8080/transactions/ai/text
```

```json
{ "transcription": "Gastei 80 reais no mercado.", "answer": "Registrei o gasto de 80 reais no mercado." }
```

### 3. Conversar por texto (testar o tool calling sem gravar áudio)

```bash
curl -X POST http://localhost:8080/transactions/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Ontem paguei 45 reais e 90 centavos no restaurante"}'
```

```bash
curl -X POST http://localhost:8080/transactions/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Quanto gastei este mês?"}'
```

### 4. Endpoints REST

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/transactions` | Cria uma transação. `amount` em centavos e `date` opcional. |
| `GET` | `/transactions/{category}` | Lista as transações de uma categoria. |
| `GET` | `/transactions/{category}/total` | Total gasto e quantidade de transações da categoria. |
| `GET` | `/transactions/summary?start=2026-11-01&end=2026-11-30` | Resumo do período. |
| `POST` | `/transactions/ai` | Áudio (`file`) para resposta em MP3. |
| `POST` | `/transactions/ai/text` | Áudio (`file`) para JSON com a transcrição e a resposta. |
| `POST` | `/transactions/ai/chat` | `{"message": "..."}` para JSON com a resposta. |

```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{"description": "Mercado", "category": "GROCERIES", "amount": 8000}'
```

Exemplo de erro de validação (`amount` negativo):

```json
{
  "type": "about:blank",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "O valor da transação deve ser maior que zero",
  "instance": "/transactions"
}
```

### 5. Testes automatizados

```bash
./gradlew test
```

- **Testes unitários e de controller:** rodam sempre, sem chave e sem Docker. O `contextLoads` usa H2 em memória.
- **Testes de integração (`*IT`):** chamam a OpenAI de verdade e só rodam quando `OPENAI_API_KEY` está definida.
  O `BudgetAssistantIT` confere de ponta a ponta que "Gastei 80 reais no mercado" vira uma transação `GROCERIES` de 8000 centavos.

## O que eu aprendi

> Escreva aqui com as suas palavras. Algumas ideias para guiar:
>
> - como o tool calling deixa a IA executar regras de negócio sem pular as camadas da aplicação;
> - por que validar no domínio protege tanto o REST quanto a IA;
> - como o prompt de sistema muda a qualidade das respostas (centavos, datas, respostas curtas);
> - como escrever testes que não dependem de uma API paga.

## Referências

- Repositório da trilha: https://github.com/digitalinnovationone/dio-spring-boot-learning-track
- Spring AI Reference: https://docs.spring.io/spring-ai/reference/index.html
- ChatClient API: https://docs.spring.io/spring-ai/reference/api/chatclient.html
- Tools API: https://docs.spring.io/spring-ai/reference/api/tools.html
- Audio Transcriptions API: https://docs.spring.io/spring-ai/reference/api/audio/transcriptions.html
- Audio Speech API: https://docs.spring.io/spring-ai/reference/api/audio/speech.html
- Conceitos de arquitetura (DDD, repository, use cases): [README principal](../README.md)

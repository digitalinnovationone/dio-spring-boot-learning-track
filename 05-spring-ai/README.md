# DIO Spring Boot - Final Project 05: Spring AI (budgeting)

## Introduction

This final module applies Spring AI in a budgeting API while preserving the same layered architecture used across the track.

The goal is to integrate AI capabilities without bypassing domain and use case boundaries.

## Code Context

The project processes voice commands to create and query financial transactions.

Primary flow:

1. Client uploads an audio file.
2. Audio is transcribed into text.
3. The model selects an application tool/use case.
4. The use case persists or queries transaction data.
5. The final response is converted to audio.

## Project Structure

- `src/main/java/dio/budgeting/domain`
  - Domain model and repository contract.
- `src/main/java/dio/budgeting/application`
  - Use cases used by both REST and AI tool calling.
- `src/main/java/dio/budgeting/infrastructure`
  - HTTP adapters, JPA adapters, and integration glue.

## Module-Specific Topics

### Speech-to-text

- Uses `TranscriptionModel` for audio transcription.
- Model settings are configured in `application.properties`.

### Tool calling

- `ChatClient` registers use-case tools.
- `@Tool` methods expose business capabilities to the model.

### Text-to-speech

- `TextToSpeechModel` produces MP3 output from final text.
- AI endpoint returns generated audio.

## Spring AI Documentation

- Spring AI Reference: https://docs.spring.io/spring-ai/reference/index.html
- ChatModel API: https://docs.spring.io/spring-ai/reference/api/chatmodel.html
- ChatClient API: https://docs.spring.io/spring-ai/reference/api/chatclient.html
- Tools API: https://docs.spring.io/spring-ai/reference/api/tools.html
- Audio Transcriptions API: https://docs.spring.io/spring-ai/reference/api/audio/transcriptions.html
- Audio Speech API: https://docs.spring.io/spring-ai/reference/api/audio/speech.html

## Shared Architecture References

Common architecture concepts are documented in the root README:

- [DDD layers](../README.md#ddd-layered-architecture)
- [Class vs record](../README.md#java-class-vs-java-record-in-domain-modeling)
- [Strong typed identifiers](../README.md#strong-typed-identifiers)
- [Repository pattern](../README.md#repository-pattern)
- [Use cases and Clean Architecture](../README.md#use-cases-and-clean-architecture)
- [Docker Compose support](../README.md#docker-compose-support-in-development)

## How to Run

Set your OpenAI API key:

```bash
export OPENAI_API_KEY="your_api_key_here"
```

Run the application and tests:

```bash
./gradlew bootRun
./gradlew test
```

## Documentação da API

O projeto expõe documentação interativa via [springdoc-openapi](https://springdoc.org/) (OpenAPI 3 + Swagger UI).

- Finalidade: visualizar e testar manualmente os três endpoints existentes (`POST /transactions`, `GET /transactions/{category}`, `POST /transactions/ai`) direto pelo navegador, sem precisar de um cliente HTTP externo.
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- JSON OpenAPI: `http://localhost:8080/v3/api-docs`

Para iniciar a aplicação (necessário para acessar o Swagger UI):

```bash
export OPENAI_API_KEY="your_api_key_here"
./gradlew bootRun
```

Descrição resumida dos endpoints:

- `POST /transactions` — cadastra uma transação a partir de um payload JSON (sem IA).
- `GET /transactions/{category}` — lista transações de uma categoria (`GROCERIES`, `PHARMA`, `AUTO`).
- `POST /transactions/ai` — recebe um áudio (`multipart/form-data`), transcreve, processa via ChatClient/Tool Calling e retorna a resposta como áudio MP3.

**Atenção**:

- O endpoint `/transactions/ai` exige a variável de ambiente `OPENAI_API_KEY` configurada no servidor.
- Cada chamada a esse endpoint realiza chamadas reais à API da OpenAI (transcrição, chat e geração de voz) e **pode gerar custo**. Não dispare esse endpoint pelo Swagger UI apenas para "testar a interface".
- Iniciar a aplicação (`bootRun`) sozinho **não** chama a OpenAI — as chamadas só ocorrem quando `/transactions/ai` é invocado.

Exemplo de uso do Swagger UI:

1. Acesse `http://localhost:8080/swagger-ui/index.html`.
2. Expanda `POST /transactions`, clique em "Try it out" e envie o exemplo de payload já preenchido.
3. Expanda `GET /transactions/{category}` e selecione uma categoria no seletor do enum.
4. Para `POST /transactions/ai`, o Swagger UI exibe um seletor de arquivo — evite enviar um áudio real a menos que você aceite o custo de uma chamada real à OpenAI.

## Notes

- Educational final project focused on AI plus architectural discipline.
- External provider integration tests may require active credentials.

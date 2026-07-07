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

## Notes

- Educational final project focused on AI plus architectural discipline.
- External provider integration tests may require active credentials.

 # 🚀 Minha Evolução no Projeto

## Melhoria implementada

Como evolução do projeto base, implementei uma validação na classe `Transaction` para impedir a criação de transações com valor menor ou igual a zero.

Antes dessa alteração, a aplicação aceitava qualquer valor informado durante a criação de uma transação. Com a validação implementada, valores inválidos passam a gerar uma exceção, garantindo maior consistência dos dados financeiros.

Trecho implementado:

```java
if (amount <= 0) {
    throw new IllegalArgumentException(
        "O valor da transação deve ser maior que zero."
    );
}
```

## Como testar a melhoria

## Como testar a melhoria

A validação foi implementada na classe `Transaction` e será executada sempre que uma nova transação for criada.

Ao informar um valor menor ou igual a zero, a aplicação lançará uma exceção impedindo o cadastro da transação.

Para testar essa funcionalidade é necessário executar a aplicação com todas as dependências configuradas, incluindo Docker Desktop e a variável de ambiente `OPENAI_API_KEY`.

Resultado esperado:

- A aplicação lançará uma `IllegalArgumentException`;
- A transação não será criada.

## Tecnologias utilizadas

- Java
- Spring Boot
- Spring AI
- Gradle
- Lombok
- Docker
- MySQL
- OpenAI API

## O que aprendi

Durante este desafio pude aprender sobre:

- Estrutura de projetos Spring Boot;
- Organização em camadas (Domain, Application e Infrastructure);
- Importação de projetos Gradle no Eclipse;
- Configuração do Lombok;
- Conceitos iniciais de Spring AI;
- Importância da validação de regras de negócio na camada de domínio.

Também compreendi que a execução completa do projeto depende da configuração do Docker Desktop e de uma chave da OpenAI (`OPENAI_API_KEY`), recursos necessários para o funcionamento do banco de dados e das funcionalidades de Inteligência Artificial. 

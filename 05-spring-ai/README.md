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

## My Challenge Evolution

### What the project does

The API receives an audio file with a financial command (e.g. "I spent 50 reais at the grocery
store"), transcribes the audio into text, uses an AI model to understand the intent, and executes a
real application tool (`@Tool`) to record or query transactions. In the end, the response is
converted back into audio.

### Improvement implemented

I chose the **"Create new Tool Calling tools"** evolution. Previously, the AI could only record a
transaction or list transactions from a specific category — there was no way to know the total
amount already spent. I added a new tool to the `ChatClient`:

- **`sum-transactions-by-category`**: sums the total amount spent in a category and reports how
  many transactions make up that total. Useful for questions like "how much have I already spent on
  pharmacy?".

To do this:

- I created the `SumTransactionsByCategoryUseCase` use case in
  `src/main/java/dio/budgeting/application`, following the same pattern as the existing use cases
  (each `@Tool` is an isolated use case that reuses the `TransactionRepository` already used by the
  other tools).
- I created the `CategoryTotalOutput` record to represent the result of the sum (category, total,
  and transaction count).
- I registered the new tool in the `ChatClient` inside `TransactionController`, alongside the
  existing tools.
- I updated the system prompt (`system-message.st`) so the model knows it can use this tool when
  asked how much has already been spent in a category.

### Technologies used

Java 25, Spring Boot, Spring AI (`ChatClient`, `@Tool`, `TranscriptionModel`, `TextToSpeechModel`),
Spring Data JPA, MySQL, JUnit 5, Mockito, and AssertJ.

### How to test the main flow

1. Start the application (`./gradlew bootRun`) with the database available (via Docker Compose).
2. Record a few transactions in the same category, either through the `POST /transactions` endpoint
   or via a voice command at `POST /transactions/ai`.
3. Send an audio file asking for the total spent in that category (e.g. "how much have I already
   spent on pharmacy?") to validate the `sum-transactions-by-category` tool.
4. You can also validate the new tool's logic in isolation, without relying on the AI, by running
   the unit test: `./gradlew test --tests "*SumTransactionsByCategoryUseCaseTest*"`.

### What I learned

I got hands-on understanding of how Spring AI's Tool Calling connects the intent interpreted by the
model to a real application function, without the `@Tool` needing to know anything about AI — it's
just a regular use case with an annotation. This reinforced how important it is to keep the layered
architecture intact even when AI is involved: business logic stays isolated in `application`, and
the language model only decides which tool to call and with which parameters.

## Notes

- Educational final project focused on AI plus architectural discipline.
- External provider integration tests may require active credentials.

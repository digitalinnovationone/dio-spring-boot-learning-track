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

## Challenge Evolution

The module evolves with a cohesive, quality-focused improvement that keeps the
same layered architecture (`domain` / `application` / `infrastructure`).

### 1. New financial queries exposed to the AI (Tool Calling)

Two new `@Tool` use cases, so the assistant can answer them **by voice** while
they are also available over REST:

- `summarize-expenses` — consolidated summary: total per category + grand total.
  - REST: `GET /transactions/summary`
- `total-by-category` — total spent in a specific category.
  - REST: `GET /transactions/total/{category}`

Both tools are registered in the `ChatClient` (`defaultTools(...)`), so a spoken
command such as *"qual meu resumo de gastos?"* triggers the real application
function.

### 2. Validation before persisting (protects REST **and** AI paths)

The AI flow calls the use case directly, bypassing the REST controller. So the
invariant lives in the **domain**: `Transaction` rejects non-positive amounts and
blank descriptions, throwing `InvalidTransactionException`. Bean Validation on
`TransactionRequest` adds an early, friendly `400` on the HTTP path, and a
`@RestControllerAdvice` (`RestExceptionHandler`) turns failures into clear
`400 Bad Request` responses instead of stack traces.

### 3. Value conversion fix (cents → reais)

`amount` is stored in **cents**, but the output previously returned `1000.00` for
R$ 10,00. It now converts correctly with `movePointLeft(2)` → `10.00`, and every
new total reuses the same conversion.

### 4. Tests for the main flows (no OpenAI required)

Unit tests use an in-memory repository (`InMemoryTransactionRepository`) and cover
the domain invariants, both new use cases, the persist use case and the cents→reais
conversion. Web and persistence layers are covered with MockMvc and H2 — no MySQL
and no OpenAI credentials needed.

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

## API Endpoints

| Method | Path                             | Description                                          |
|--------|----------------------------------|------------------------------------------------------|
| POST   | `/transactions`                  | Create a transaction (JSON, `amount` in **cents**).  |
| GET    | `/transactions/{category}`       | List transactions of a category.                     |
| GET    | `/transactions/total/{category}` | **New** — total spent in a category.                 |
| GET    | `/transactions/summary`          | **New** — total per category + grand total.          |
| POST   | `/transactions/ai`               | Voice flow: audio in → AI + tools → audio (MP3) out. |

Categories: `GROCERIES`, `PHARMA`, `AUTO`.

Ready-to-run requests for every endpoint (including validation errors and the
audio flow) are in [`docs/requests.http`](docs/requests.http) — open it in
IntelliJ or VS Code (REST Client) and click *Send Request*.

## How to Run

Prerequisites: only the JDK is needed to build — `settings.gradle` enables the
Gradle **foojay** toolchain resolver, so the matching Java 25 toolchain is
downloaded automatically when it is missing.

### Option A — Quick start, no Docker

Runs with an in-memory H2 database. No MySQL, no Docker, only the JDK:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

The app starts on `http://localhost:8080`. The voice flow (`/transactions/ai`)
additionally needs an OpenAI key.

### Option B — Full setup (MySQL via Docker Compose)

Set your OpenAI API key (required for the voice/AI flow):

```bash
export OPENAI_API_KEY="your_api_key_here"
./gradlew bootRun
```

Spring Boot starts the MySQL container defined in `compose.yml` automatically.

## Testing

The test suite runs green without any external dependency:

```bash
# Full suite — unit + web + persistence (H2) + context load.
# OpenAI integration tests are skipped automatically when OPENAI_API_KEY is absent.
./gradlew test

# Only the fast unit tests (domain + use cases), no Spring context:
./gradlew test --tests "dio.budgeting.domain.*" --tests "dio.budgeting.application.*"
```

What is covered:

- **Domain** — invariants (`amount > 0`, non-blank description).
- **Use cases** — persist, summary, category total, cents→reais conversion.
- **Web layer** — `TransactionController` via MockMvc: routing, `400` on invalid
  input, `RestExceptionHandler`.
- **Persistence** — `JpaTransactionRepository` against H2 (entity↔domain mapping).
- **Context** — `contextLoads` boots the full app with H2 (no MySQL/OpenAI needed).
- **Integration (OpenAI)** — `*IT` tests run only when `OPENAI_API_KEY` is set.

## Notes

- Educational final project focused on AI plus architectural discipline.
- External provider integration tests may require active credentials.

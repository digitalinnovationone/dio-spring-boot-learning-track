# DIO Spring Boot - Final Project 05: Spring AI (budgeting)

## Introduction

This final module applies Spring AI in a budgeting API while preserving the same layered architecture used across the track.

The goal is to integrate AI capabilities without bypassing domain and use case boundaries.

## My Improvement

As part of the DIO Spring Boot + Spring AI challenge, I implemented a new feature that calculates the total amount spent in a specific transaction category.

The feature was implemented through a new `TotalByCategoryUseCase`, which retrieves all transactions from a category and calculates their total amount.

The functionality is available through the REST API:

```http
GET /transactions/{category}/total
```

Example:

```http
GET /transactions/GROCERIES/total
```

Response:

```json
{
  "category": "GROCERIES",
  "total": 80
}
```
### Tests Performed

The following flows were successfully tested:

- Creating transactions with `POST /transactions`.
- Listing transactions by category with `GET /transactions/{category}`.
- Calculating the total by category with `GET /transactions/{category}/total`.
- Two `GROCERIES` transactions with values `50` and `30` correctly returned a total of `80`.
- The `/transactions/ai` endpoint was tested using an audio request.
- The Spring AI Tool Calling flow successfully used the new total-by-category functionality.
- Gradle automated tests were executed successfully with `BUILD SUCCESSFUL`.

### Technologies Used

- Java 25
- Spring Boot
- Spring AI
- Spring Data JPA
- MySQL
- Docker
- Gradle
- OpenAI API
- Postman
- Git and GitHub

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

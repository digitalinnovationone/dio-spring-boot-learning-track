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

## Upload de áudio

`POST /transactions/ai` recebe o comando de voz como `multipart/form-data`, na parte obrigatória `file`.

Antes de qualquer chamada à OpenAI (transcrição, ChatClient, geração de voz), o arquivo é validado localmente por `AudioFileValidator` (`dio.budgeting.infrastructure.http.audio`):

1. arquivo presente e não vazio;
2. tamanho dentro do limite (`app.audio.max-size`, padrão **10 MB**);
3. `content type` presente;
4. `content type` entre os aceitos: `audio/mpeg`, `audio/mp3`, `audio/mp4`, `audio/m4a`, `audio/x-m4a`, `audio/wav`, `audio/x-wav`, `audio/webm`.

Um arquivo que falhe em qualquer uma dessas regras é rejeitado **sem gerar custo**: nenhuma chamada a `TranscriptionModel`, `ChatClient` ou `TextToSpeechModel` ocorre para um upload inválido.

Exemplo de requisição válida (adaptar caminho do arquivo):

```bash
curl -X POST \
  http://localhost:8080/transactions/ai \
  -H "Content-Type: multipart/form-data" \
  -F "file=@comando.mp3;type=audio/mpeg"
```

**Erros possíveis**:

| Situação | Status | Título |
| -------- | -----: | ------ |
| Arquivo vazio, sem `content type` ou tipo não permitido | 400 | Arquivo de áudio inválido |
| Parte `file` ausente | 400 | Arquivo obrigatório |
| Requisição não multipart / multipart malformado | 400 | Requisição inválida |
| Arquivo acima de 10 MB (validado localmente ou rejeitado pelo servidor) | 413 | Arquivo muito grande |

**Atenção**: esta validação verifica apenas o `content type` declarado pelo cliente, não o conteúdo binário do arquivo — um arquivo renomeado (ex.: `.exe` enviado como `audio/mpeg`) não é detectado. Uploads que passam na validação ainda geram chamadas reais e potencialmente pagas à API da OpenAI.

## Monetary Values

- `amount` is represented as `BigDecimal`, in **reais**, always normalized to **two decimal places** (`RoundingMode.HALF_UP`), from the domain (`Transaction`) through persistence (`DECIMAL(19,2)`) and both REST/Tool Calling responses.
- Example request: `{"description": "Combustível", "amount": 80.90, "category": "AUTO"}` → response: `{"id": "...", "category": "AUTO", "description": "Combustível", "amount": 80.90}`.
- Values are rounded, not truncated, when they carry more than two decimal places (e.g. `80.905` becomes `80.91`); the Swagger schema documents `amount` as a decimal number.
- **Local database note**: if a local MySQL instance was already running with the previous `BIGINT` column, the schema won't be auto-migrated safely by Hibernate (`ddl-auto=update` does not convert `BIGINT` to `DECIMAL`). Recreate the local dev database (or manually `ALTER TABLE`) before running against a pre-existing schema.

## Validations

Business invariants are enforced centrally in the domain (`Transaction`), so both REST and Tool Calling go through the same protection — the rules do not depend on controller/DTO annotations alone:

- **Description**: required, cannot be null/empty/blank; leading and trailing spaces are stripped (`"  Combustível  "` is stored as `"Combustível"`).
- **Amount**: required, must be greater than zero **after** normalization to two decimals (`HALF_UP`) — e.g. `0.004` rounds to `0.00` and is rejected, while `0.005` rounds to `0.01` and is accepted.
- **Category**: required, restricted to the current enum values (`GROCERIES`, `PHARMA`, `AUTO`).

Invalid input throws `InvalidTransactionException` (a domain exception, no HTTP concept involved) before any repository call. Bean Validation annotations (`@NotBlank`, `@NotNull`, `@DecimalMin`) were added to `TransactionRequest` as a first HTTP-level barrier, causing `POST /transactions` to reject invalid payloads with `400 Bad Request` — but this is a convenience layer, not the source of truth.

Valid request example:
```json
{"description": "Combustível", "amount": 80.90, "category": "AUTO"}
```
Invalid request examples (all rejected):
```json
{"description": "", "amount": 80.90, "category": "AUTO"}
{"description": "Combustível", "amount": 0.00, "category": "AUTO"}
{"description": "Combustível", "amount": -10.00, "category": "AUTO"}
```

## Tratamento de erros

Todos os endpoints REST retornam erros em um contrato único e previsível, baseado no padrão nativo do Spring (`ProblemDetail`, RFC 9457), implementado por um `@RestControllerAdvice` (`GlobalExceptionHandler`) na camada de infraestrutura HTTP. A exceção de domínio (`InvalidTransactionException`) não conhece HTTP: quem traduz negócio em status code é sempre o handler global.

**Campos comuns**: `type`, `title`, `status`, `detail`, `instance` (caminho da requisição), `timestamp` (ISO 8601). A propriedade `errors` (lista de `{field, message}`) só aparece quando há falhas de validação de campo.

### Erros de validação (Bean Validation)

`POST /transactions` com payload inválido retorna todos os erros de campo, ordenados por nome do campo:

```json
{
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos possuem valores inválidos.",
  "instance": "/transactions",
  "errors": [
    {
      "field": "amount",
      "message": "O valor da transação deve ser maior que zero."
    }
  ]
}
```

### Categoria inválida

- No corpo (`POST /transactions`), quando `category` não corresponde a um valor do enum, ou na rota (`GET /transactions/{category}`), a resposta indica os valores aceitos, por exemplo: `"Categoria inválida. Valores aceitos: GROCERIES, PHARMA, AUTO."`.

### JSON inválido

- JSON malformado ou tipos incompatíveis (ex.: `amount` como texto) retornam `400` com título `Requisição inválida` e uma mensagem genérica e segura — sem nomes de classes internas, caminhos ou detalhes do Jackson.

### Upload de áudio (`POST /transactions/ai`)

- Parte `file` ausente retorna `400` com título `Arquivo obrigatório`.
- Requisição não multipart ou malformada retorna `400` com título `Requisição inválida`.
- Arquivo vazio, sem `content type` ou com tipo não permitido retorna `400` com título `Arquivo de áudio inválido` (ver [seção de upload de áudio](#upload-de-áudio) para a lista completa de regras).
- Arquivo acima do tamanho máximo (validado localmente ou rejeitado pelo servidor via `MaxUploadSizeExceededException`) retorna `413` com título `Arquivo muito grande`.
- Em todos os casos acima, nenhuma chamada a transcrição, ChatClient ou geração de voz ocorre.

### Erro interno

- Falhas inesperadas retornam `500` com título `Erro interno` e mensagem genérica; o erro completo é registrado no servidor via SLF4J, nunca exposto ao cliente. Stack traces e mensagens técnicas nunca aparecem no corpo da resposta.

## Falhas da integração com IA

`POST /transactions/ai` encadeia três chamadas externas — transcrição, `ChatClient` (com Tool Calling) e geração de voz — cada uma classificada e traduzida para um `ProblemDetail` padronizado pelo `GlobalExceptionHandler`, via `AiIntegrationException` lançada pelo `AiTransactionProcessor` (`dio.budgeting.infrastructure.ai`).

| Etapa | Motivo | Status | Título |
| --- | --- | -----: | --- |
| Transcrição | áudio sem fala identificável (texto nulo/vazio) | 422 | Áudio não processável |
| Qualquer etapa | timeout de conexão/leitura | 504 | Tempo limite excedido |
| Qualquer etapa | limite de requisições do provedor (HTTP 429) | 503 | Serviço de IA temporariamente indisponível |
| Qualquer etapa | provedor indisponível (HTTP 5xx, conexão recusada) | 503 | Serviço de IA temporariamente indisponível |
| Chat / TTS | resposta vazia ou estruturalmente inválida do provedor | 502 | Resposta inválida do serviço de IA |
| Qualquer etapa | falha externa não classificada | 502 | Falha na integração com IA |
| Qualquer etapa | credencial/configuração inválida (HTTP 401/403) | 500 | Erro interno |

Um HTTP 429 do provedor **nunca** vira `429` na resposta desta API — o limite é da conta/organização OpenAI, não da API local; é tratado como `503` com uma mensagem que sugere tentar novamente mais tarde.

**Timeout**: `spring.http.clients.connect-timeout` (10s) / `spring.http.clients.read-timeout` (60s) em `application.properties`. É uma configuração **global única**, compartilhada pelos três modelos (`ChatModel`, `TranscriptionModel`, `TextToSpeechModel`) — o Spring AI injeta o mesmo `RestClient.Builder`/`HttpClientSettings` nos três, não havendo suporte nativo a um timeout diferente por modelo sem reescrever manualmente cada bean (fora de escopo desta tarefa).

**Retry**: o Spring AI já retenta automaticamente (`spring-ai-retry`/`SpringAiRetryAutoConfiguration`) falhas transitórias (HTTP 5xx, timeout/conexão) via um `RetryTemplate` **compartilhado** pelos três modelos — inclusive o `ChatClient`. Isso não duplica Tool Calling: o retry atua apenas na chamada HTTP individual; a execução do `@Tool` (`persist-transaction`) é um método Java local, disparado uma única vez por `tool_call`, fora do laço de retry (verificado em `DefaultToolCallingManager`/`MethodToolCallback`). As tentativas foram reduzidas do default (10, backoff até 3 min — inviável para um endpoint síncrono) para `spring.ai.retry.max-attempts=2` (1 tentativa adicional) com backoff curto (`spring.ai.retry.backoff.*`). HTTP 4xx (401, 429 etc.) nunca é retentado automaticamente.

**Tool Calling e domínio**: com a configuração padrão do Spring AI (`DefaultToolExecutionExceptionProcessor`, `alwaysThrow=false`), uma exceção de domínio lançada pelas tools (`InvalidTransactionException`) é convertida em texto e devolvida ao modelo como resultado da tool — nunca propaga como exceção Java através do `ChatClient`. Um comando de voz inválido (ex.: valor zero) vira uma resposta em áudio explicando o problema, não um erro de integração de IA.

**Risco conhecido de duplicação**: se o Tool Calling já persistiu a transação e a geração de voz falhar **depois**, a API retorna um erro padronizado (502/503/504) mesmo com a transação já salva. Um cliente que reenvie o comando de voz após esse erro pode gerar uma transação duplicada. Esta tarefa não implementa idempotência — fica registrada como limitação para uma tarefa futura.

**Testes**: `AiTransactionProcessorTest` (unitário, mocks de `TranscriptionModel`/`ChatClient`/`TextToSpeechModel`, sem chamada real) e `OpenAiFailureHandlingTest` (MockMvc, `AiTransactionProcessor` mockado como caixa-preta) cobrem toda a classificação acima. Nenhum teste usa API key real ou chama a OpenAI.

## Idempotência do processamento por áudio

`POST /transactions/ai` pode ser chamado mais de uma vez para o **mesmo comando de voz** (clique duplo, timeout percebido pelo cliente, queda de conexão, retry manual) — sem o header `Idempotency-Key`, cada reenvio repetiria transcrição, `ChatClient`/Tool Calling e a persistência da transação, podendo duplicá-la.

**Uso**: envie um header `Idempotency-Key` (string opaca gerada pelo cliente, até 128 caracteres, apenas letras/números/`._:-`) com o mesmo valor para reenviar o *mesmo* comando:

```bash
curl -X POST \
  http://localhost:8080/transactions/ai \
  -H "Idempotency-Key: 3f18cfbe-6072-4e5c-b7a6-183ae1809846" \
  -F "file=@comando.m4a;type=audio/mp4"
```

- **Header ausente** → `400` "Chave idempotente ausente".
- **Formato inválido** (vazio, só espaços, acima de 128 caracteres, caracteres fora de `[A-Za-z0-9._:-]`) → `400` "Chave idempotente inválida".
- **Mesma chave, mesmo arquivo, operação já concluída** → a resposta é reconstruída (novo TTS a partir do texto final já gerado) **sem** repetir transcrição, ChatClient ou Tool Calling. A resposta inclui o header `Idempotency-Replayed: true` (a primeira chamada retorna `false`).
- **Mesma chave, arquivo diferente** → `409` "Chave idempotente em conflito" (a chave nunca é reaproveitada para um comando diferente).
- **Mesma chave, operação ainda em processamento** (segunda requisição concorrente) → `409` "Operação em processamento".
- **Mesma chave, falha anterior**: se a falha ocorreu na transcrição (nenhum efeito colateral possível ainda), a mesma chave pode ser reenviada normalmente. Se a falha ocorreu no chat ou na geração de voz (Tool Calling pode já ter persistido a transação), a chave fica bloqueada (`409` "Reprocessamento não permitido") — é necessário usar uma nova chave.

**Concorrência**: a proteção real é uma constraint única de banco em `idempotency_key` (não uma checagem em memória). Duas requisições simultâneas com uma chave nova podem ambas passar pela checagem inicial; a constraint garante que só uma consegue persistir a operação, e a outra recebe "operação em processamento".

**O que não é feito**: o áudio (enviado ou gerado) nunca é armazenado — apenas o texto final e seguro do ChatClient, usado para regenerar o áudio em um replay.

**Risco residual**: se o Tool Calling já persistiu a transação e a geração de voz falhar depois, a operação é marcada como falha bloqueada para retry — a transação criada não é desfeita automaticamente (sem compensação nesta tarefa).

### Expiração e limpeza (TASK-010)

Chaves não são retidas para sempre: cada operação tem uma janela de retenção configurável, depois da qual o registro é removido e a chave pode ser reutilizada como se fosse uma operação nova.

| Estado | Janela (padrão) | Ação após expiração |
| ------ | ---------------- | -------------------- |
| `COMPLETED` | 24h (`app.idempotency.completed-retention`) | Removida; a chave pode iniciar uma nova operação |
| `FAILED` (qualquer estágio) | 24h (`app.idempotency.failed-retention`) | Removida; a chave pode iniciar uma nova operação |
| `PROCESSING` | 15 min de inatividade (`app.idempotency.processing-timeout`) | Considerada abandonada e recuperada (nunca reprocessada automaticamente) |

**Aviso importante**: a idempotência é garantida apenas durante a janela configurada. Após a expiração e remoção do registro, a mesma chave poderá ser tratada como uma nova operação — reenviar o mesmo comando de voz depois desse ponto pode gerar uma nova transação. Use uma chave nova por intenção de comando; nunca reutilize propositalmente uma chave antiga esperando bloqueio eterno.

**Operações `PROCESSING` abandonadas** (ex.: a aplicação caiu no meio do processamento) são recuperadas com base no estágio alcançado, registrado em `currentStage` (`REGISTERED`/`TRANSCRIPTION`/`CHAT`/`SPEECH`, atualizado antes de cada chamada externa — transcrição, ChatClient/Tool Calling, geração de voz):

- **`REGISTERED` ou `TRANSCRIPTION`** (Tool Calling ainda não rodou): a operação é marcada `FAILED`/`TRANSCRIPTION` e a **mesma chave permite retry** na próxima requisição, seguindo a política já existente.
- **`CHAT` ou `SPEECH`** (Tool Calling pode já ter persistido uma transação): a operação é marcada `FAILED` no estágio correspondente e o **retry continua bloqueado** — nunca presumimos que nenhum efeito colateral ocorreu.
- **Estágio desconhecido/nulo** (linha de um schema anterior a esta tarefa): tratado de forma conservadora como inseguro, igual a `CHAT`/`SPEECH`.

Essa recuperação acontece de duas formas, sempre com a mesma política (`IdempotencyExpirationPolicy`), nunca duplicada:

- **Oportunisticamente**, dentro do próprio `begin()` — uma operação abandonada há horas não espera a próxima execução do scheduler para ser resolvida.
- **Periodicamente**, via `IdempotencyCleanupScheduler` (`@Scheduled`, intervalo `app.idempotency.cleanup-interval`), que primeiro recupera `PROCESSING` abandonadas e depois remove `COMPLETED`/`FAILED` expiradas, uma transação curta por linha (nunca mantém transação aberta durante qualquer chamada externa — não há chamada externa nesse fluxo).

**Configuração** (`application.properties`, todos sob `app.idempotency.*`, tipados via `IdempotencyProperties`/`Duration`, validados na inicialização - duração zero ou negativa falha o startup):

```properties
app.idempotency.completed-retention=24h
app.idempotency.failed-retention=24h
app.idempotency.processing-timeout=15m
app.idempotency.cleanup-interval=PT1H
app.idempotency.cleanup-enabled=true
app.idempotency.batch-size=100
```

`cleanup-interval` usa formato ISO-8601 (`PT1H`) propositalmente: além de ser lido como `Duration` tipado, o mesmo valor é lido cru por `@Scheduled(fixedDelayString = ...)`, que só entende ISO-8601 ou milissegundos — não os sufixos simples (`24h`) usados nas outras propriedades.

No perfil de teste (`application-test.properties`), `app.idempotency.cleanup-enabled=false` desativa o bean do scheduler (`@ConditionalOnProperty`) para que nenhuma limpeza automática rode em paralelo com os testes; os testes chamam `IdempotencyCleanupService`/`IdempotencyCleanupScheduler` diretamente.

**Relógio**: toda leitura de hora nesta área passa por um único `Clock` injetável (`Clock.systemUTC()` em produção), nunca `OffsetDateTime.now()` espalhado pelo código — o que permite testes determinísticos com `Clock.fixed` sem `Thread.sleep`.

**Concorrência entre scheduler e requisição**: cada linha usa `@Version` (lock otimista já existente). Se o scheduler tentar recuperar uma linha que a própria requisição acabou de concluir/falhar, o conflito gera `OptimisticLockingFailureException`, que o scheduler apenas registra e ignora — a linha é reavaliada na próxima execução, nunca derruba o lote inteiro.

**Concorrência entre limpeza e replay**: a exclusão em lote revalida `status`+`updatedAt` na própria cláusula `WHERE` no momento do delete, então uma linha que mudou de estado entre a consulta e a exclusão (ex.: um retry que voltou `FAILED` → `PROCESSING`) nunca é removida por engano. Um replay em andamento não é afetado: o texto necessário (`responseText`) já é lido antes de qualquer exclusão.

**Índices**: `(status, updated_at)` suporta as consultas de expiração/abandono sem varrer a tabela inteira; a constraint única em `idempotency_key` permanece a proteção real contra duplicação.

## Testes automatizados

O projeto combina três níveis de teste, cada um cobrindo uma responsabilidade diferente:

- **Testes unitários** (`TransactionValidationTest`, `PersistTransactionUseCaseTest`, `MonetaryAmountBigDecimalTest`, `AudioFileValidatorTest`, `GlobalExceptionHandlerTest`) — validam regras de domínio, casos de uso e o handler de erros isoladamente, com mocks, sem subir o contexto Spring inteiro (ou subindo-o com `TransactionRepository`/beans de IA mockados).
- **Testes HTTP com contrato mockado** (`TransactionControllerValidationTest`, `TransactionAudioUploadTest`, `SwaggerDocumentationTest`) — usam `MockMvc` contra o `TransactionController` real, mas com `TransactionRepository` mockado (`@MockitoBean`) e a autoconfiguração de JPA/DataSource excluída; comprovam o contrato HTTP (validação, status, `ProblemDetail`, documentação OpenAPI) sem tocar em persistência.
- **Testes de integração REST** (`TransactionRestIntegrationTest`) — percorrem o fluxo completo e real: `MockMvc` → `TransactionController` → `PersistTransactionUseCase`/`ListTransactionsByCategoryUseCase` → `JpaTransactionRepository` → `TransactionEntityRepository` (Spring Data JPA) → banco H2 em memória, com resposta HTTP serializada de volta. Nenhum componente de negócio é mockado aqui; apenas o banco é isolado.
- **Testes de resiliência da integração com IA** (`AiTransactionProcessorTest`, `OpenAiFailureHandlingTest`) — cobrem a classificação de falhas de transcrição/chat/voz e o mapeamento para `ProblemDetail` (ver [seção de falhas da integração com IA](#falhas-da-integração-com-ia)). Sem chamada real à OpenAI.
- **Testes de idempotência** (`AudioPayloadFingerprintTest`, `AudioCommandIdempotencyServiceTest`, `TransactionAudioIdempotencyTest`) — cobrem validação da chave, fingerprint SHA-256, replay/conflito/em-processamento/política de retry e a constraint única no H2 (ver [seção de idempotência](#idempotência-do-processamento-por-áudio)). Sem chamada real à OpenAI.
- **Testes de expiração e limpeza** (`IdempotencyPropertiesTest`, `IdempotencyExpirationPolicyTest`, `IdempotencyCleanupServiceTest`, `IdempotencyCleanupSchedulerTest`, mais os cenários de recuperação/expiração/lock otimista adicionados a `AudioCommandIdempotencyServiceTest`) — cobrem validação de propriedades, limites de janela com `Clock.fixed`, recuperação de `PROCESSING` abandonada por estágio, remoção em lote com H2 real e conflito de `@Version` (ver [seção de expiração e limpeza](#expiração-e-limpeza-task-010)). Scheduler nunca roda automaticamente nos testes; sem chamada real à OpenAI.

**Banco de teste**: os testes de integração usam H2 em memória (`com.h2database:h2`, dependência apenas em `testRuntimeOnly`), configurado no perfil `test` (`src/test/resources/application-test.properties`, modo de compatibilidade MySQL, schema recriado via `ddl-auto=create-drop`). Não depende de MySQL local nem de Docker. Cada teste roda dentro de uma transação com rollback automático (`@Transactional`), garantindo isolamento sem necessidade de limpeza manual.

**Sem chamadas à OpenAI**: os beans de IA (`ChatClient`, `TranscriptionModel`, `TextToSpeechModel`) sobem normalmente no contexto de teste com uma API key fictícia (`spring.ai.openai.api-key=test-key-not-a-real-credential`) — a simples construção desses beans não dispara chamada de rede. O endpoint `POST /transactions/ai` não é testado de ponta a ponta nesta suíte; apenas um teste de sanidade confirma que a rota existe e retorna `400` antes de qualquer chamada externa.

Executar apenas a suíte de integração:

```powershell
.\gradlew.bat test --tests "dio.budgeting.TransactionRestIntegrationTest"
```

Executar todos os testes do módulo:

```powershell
.\gradlew.bat test
```

## Notes

- Educational final project focused on AI plus architectural discipline.
- External provider integration tests may require active credentials.

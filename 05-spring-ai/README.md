# DIO Spring Boot - Projeto Final 05: Spring AI (budgeting)

## Introdução
Este módulo final aplica o Spring AI em uma API de orçamento (budgeting), preservando a mesma arquitetura em camadas usada ao longo da trilha.
O objetivo é integrar capacidades de IA sem contornar as fronteiras de domínio e de use case.

## Contexto do Código
O projeto processa comandos de voz para criar e consultar transações financeiras.

Fluxo principal:
1. O cliente envia um arquivo de áudio.
2. O áudio é transcrito em texto.
3. O modelo seleciona uma tool/use case da aplicação.
4. O use case persiste ou consulta os dados da transação.
5. A resposta final é convertida em áudio.

Os mesmos use cases também sustentam um fluxo REST tradicional (`POST /transactions`,
`GET /transactions/{category}`), então toda regra de negócio precisa valer tanto para o ponto de
entrada REST quanto para o ponto de entrada via IA (tool calling).

## Estrutura do Projeto
- `src/main/java/dio/budgeting/domain`
  - Modelo de domínio e contrato do repositório.
- `src/main/java/dio/budgeting/application`
  - Use cases utilizados tanto pelo REST quanto pelo tool calling da IA.
- `src/main/java/dio/budgeting/infrastructure`
  - Adaptadores HTTP, adaptadores JPA e a integração entre as camadas.

## Tópicos Específicos do Módulo

### Speech-to-text
- Usa `TranscriptionModel` para transcrição de áudio.
- As configurações do modelo ficam em `application.properties`.

### Tool calling
- `ChatClient` registra as tools dos use cases.
- Métodos anotados com `@Tool` expõem capacidades de negócio ao modelo.

### Text-to-speech
- `TextToSpeechModel` gera saída em MP3 a partir do texto final.
- O endpoint de IA retorna o áudio gerado.

## Melhoria Implementada: Validação de Transações

Para este desafio escolhi **"Adicionar validações antes de salvar uma transação"** — uma melhoria
pequena e bem delimitada, que fecha uma lacuna real: nada impedia que uma transação fosse criada
com descrição em branco, valor zero/negativo ou categoria nula, seja pelo endpoint REST, seja pelo
fluxo de tool calling da IA.

- **Validação no domínio (`Transaction`)** — o construtor de criação
  (`description, amount, category`) agora valida as regras de negócio e lança
  `IllegalArgumentException` com uma mensagem clara. Como tanto o `PersistTransactionUseCase`
  (REST) quanto o fluxo anotado com `@Tool` (IA) acabam criando uma `Transaction`, isso funciona
  como uma única fonte de verdade que protege os dois pontos de entrada ao mesmo tempo — o
  `PersistTransactionInput` gerado pela IA nunca passa pelo `@Valid`, então depender apenas da
  validação HTTP deixaria esse caminho desprotegido.
- **Validação na camada HTTP (`TransactionRequest`)** — adicionei `@NotBlank`, `@NotNull` e
  `@Positive` (Jakarta Bean Validation), além de `@Valid` em
  `TransactionController#createTransaction`, para que campos ausentes ou mal formatados no JSON
  sejam rejeitados com 400 antes mesmo de chegar ao domínio.
- **Tratamento de erros centralizado** — criei um `GlobalExceptionHandler`
  (`@RestControllerAdvice`) que transforma `MethodArgumentNotValidException` (do `@Valid`) e
  `IllegalArgumentException` (do domínio) em um `ErrorResponse` padronizado (timestamp, status,
  mensagem e detalhes), em vez de um 500 genérico.

Arquivos alterados: `domain/Transaction.java`, `infrastructure/http/request/TransactionRequest.java`,
`infrastructure/http/TransactionController.java`.

Arquivos novos: `infrastructure/http/exception/GlobalExceptionHandler.java`,
`infrastructure/http/exception/ErrorResponse.java`,
`test/domain/TransactionTest.java`, `test/application/PersistTransactionUseCaseTest.java`.


## Documentação do Spring AI
- Spring AI Reference: https://docs.spring.io/spring-ai/reference/index.html
- ChatModel API: https://docs.spring.io/spring-ai/reference/api/chatmodel.html
- ChatClient API: https://docs.spring.io/spring-ai/reference/api/chatclient.html
- Tools API: https://docs.spring.io/spring-ai/reference/api/tools.html
- Audio Transcriptions API: https://docs.spring.io/spring-ai/reference/api/audio/transcriptions.html
- Audio Speech API: https://docs.spring.io/spring-ai/reference/api/audio/speech.html

## Referências de Arquitetura Compartilhadas
Conceitos de arquitetura em comum estão documentados no README raiz:
- [Camadas DDD](../README.md#ddd-layered-architecture)
- [Class vs record em Java](../README.md#java-class-vs-java-record-in-domain-modeling)
- [Identificadores fortemente tipados](../README.md#strong-typed-identifiers)
- [Padrão Repository](../README.md#repository-pattern)
- [Use cases e Clean Architecture](../README.md#use-cases-and-clean-architecture)
- [Suporte a Docker Compose](../README.md#docker-compose-support-in-development)

## Como Executar
Configure sua chave de API da OpenAI:
```bash
export OPENAI_API_KEY="sua_api_key_aqui"
```
Execute a aplicação e os testes:
```bash
./gradlew bootRun
./gradlew test
```

## Como Testar o Fluxo Principal

### Testes automatizados (regras de validação)
```bash
./gradlew test
```
- `TransactionTest` — garante que uma transação válida é criada corretamente e que descrição em
  branco, valor ≤ 0 ou categoria nula disparam `IllegalArgumentException`.
- `PersistTransactionUseCaseTest` — usa um `TransactionRepository` fake em memória para verificar
  que uma transação válida é persistida e que uma inválida nunca chega ao repositório.

### Testes manuais via REST
Criar uma transação válida:
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{"description":"Mercado", "category":"GROCERIES", "amount":15000}'
# 201 Created
```
Tentar criar uma inválida (valor negativo):
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{"description":"Mercado", "category":"GROCERIES", "amount":-10}'
# 400 Bad Request, com detalhes apontando o campo "amount"
```
Listar por categoria:
```bash
curl http://localhost:8080/transactions/GROCERIES
```

### Fluxo de IA
```bash
curl -X POST http://localhost:8080/transactions/ai \
  -F "file=@audio-exemplo.mp3" \
  --output resposta.mp3
```
Pedir ao assistente (por voz) para registrar uma transação com valor zero ou sem categoria também
deve ser rejeitado — a validação no domínio (`Transaction`) protege esse caminho, mesmo ele nunca
passando pelo `@Valid`.

## O que Aprendi
- Onde a validação deve ficar quando um use case tem **dois pontos de entrada** (REST e tool
  calling de IA): depender apenas do `@Valid` na borda HTTP deixaria o fluxo guiado por IA
  desprotegido, então a regra de negócio precisa viver no domínio (`Transaction`) como fonte
  única de verdade, com o `@Valid` funcionando como uma verificação adicional e mais cedo no lado
  REST.
- Usar `@RestControllerAdvice` para centralizar o tratamento de erros em vez de espalhar
  `try/catch` pelos controllers, retornando um formato de erro consistente.
- A diferença prática entre validação "sintática" (Bean Validation — presença/formato de campos)
  e validação "semântica"/de negócio (regras de domínio), e por que as duas se complementam em
  vez de serem redundantes.
- Como o tool calling do Spring AI reaproveita exatamente os mesmos use cases do controller REST,
  de forma que uma correção em um único lugar (o domínio) reforça automaticamente os dois canais
  (JSON e voz).

## Observações
- Projeto final educacional focado em IA aliada à disciplina arquitetural.
- Testes de integração com provedores externos podem exigir credenciais ativas.
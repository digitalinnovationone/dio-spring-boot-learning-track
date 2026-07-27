package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.ai.AiIntegrationException;
import dio.budgeting.infrastructure.ai.AiTransactionProcessor;
import dio.budgeting.infrastructure.http.audio.AudioFileValidator;
import dio.budgeting.infrastructure.http.error.ApiErrorResponse;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import dio.budgeting.infrastructure.idempotency.AudioCommandIdempotencyService;
import dio.budgeting.infrastructure.idempotency.AudioPayloadFingerprint;
import dio.budgeting.infrastructure.idempotency.IdempotencyDecision;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Cadastro e consulta de transações financeiras, via REST tradicional ou comando de voz (transcrição + IA + Tool Calling + texto-para-voz)")
public class TransactionController {
    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;

    private final AudioFileValidator audioFileValidator;
    private final AiTransactionProcessor aiTransactionProcessor;
    private final AudioCommandIdempotencyService idempotencyService;

    public TransactionController(PersistTransactionUseCase persistTransactionUseCase,
                                 ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                 AudioFileValidator audioFileValidator,
                                 AiTransactionProcessor aiTransactionProcessor,
                                 AudioCommandIdempotencyService idempotencyService) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.audioFileValidator = audioFileValidator;
        this.aiTransactionProcessor = aiTransactionProcessor;
        this.idempotencyService = idempotencyService;
    }

    @Operation(
            summary = "Cadastra uma transação financeira",
            description = "Persiste uma nova transação a partir de um payload JSON, sem transcrição de áudio nem IA."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Transação criada com sucesso",
                    content = @Content(
                            schema = @Schema(implementation = TransactionResponse.class),
                            examples = @ExampleObject(
                                    name = "Combustível",
                                    value = """
                                            {
                                              "id": "3f1c9e2a-2b8b-4e0f-8b7a-1f2e3d4c5b6a",
                                              "category": "AUTO",
                                              "description": "Combustível",
                                              "amount": 80.90
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Payload inválido: descrição vazia, valor nulo/zero/negativo, categoria nula ou "
                            + "inexistente, ou JSON malformado.",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Dados inválidos",
                                    value = """
                                            {
                                              "type": "about:blank",
                                              "title": "Dados inválidos",
                                              "status": 400,
                                              "detail": "Um ou mais campos possuem valores inválidos.",
                                              "instance": "/transactions",
                                              "timestamp": "2026-07-24T18:00:00-03:00",
                                              "errors": [
                                                {
                                                  "field": "amount",
                                                  "message": "O valor da transação deve ser maior que zero."
                                                }
                                              ]
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro inesperado ao processar a requisição.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@Valid @RequestBody TransactionRequest request) {
        var transaction = persistTransactionUseCase.execute(request.toInput());
        return TransactionResponse.from(transaction);
    }

    @Operation(
            summary = "Lista transações por categoria",
            description = "Retorna todas as transações persistidas para a categoria informada. Retorna lista vazia quando não há registros."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de transações da categoria (pode ser vazia)",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Categoria inexistente no enum atual.",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Parâmetro inválido",
                                    value = """
                                            {
                                              "type": "about:blank",
                                              "title": "Parâmetro inválido",
                                              "status": 400,
                                              "detail": "O valor 'FOOD' não é válido para o parâmetro 'category'. Valores aceitos: GROCERIES, PHARMA, AUTO.",
                                              "instance": "/transactions/FOOD",
                                              "timestamp": "2026-07-24T18:00:00-03:00"
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro inesperado ao processar a requisição.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/{category}")
    public List<TransactionResponse> readTransactions(
            @Parameter(description = "Categoria da transação, conforme o enum atual do domínio", required = true)
            @PathVariable Category category) {
        return listTransactionsByCategoryUseCase.execute(category).stream().map(TransactionResponse::from).toList();
    }

    @Operation(
            summary = "Registra uma transação por comando de voz",
            description = """
                    Fluxo completo de IA: recebe um áudio, transcreve com o modelo de transcrição da OpenAI (whisper-1), \
                    envia o texto transcrito ao ChatClient (gpt-4o-mini), que pode acionar as ferramentas \
                    "persist-transaction" ou "list-transactions-by-category" via Tool Calling, e converte a resposta \
                    final em áudio MP3 (gpt-4o-mini-tts) para retorno ao cliente.

                    Requer a variável de ambiente OPENAI_API_KEY configurada no servidor. Cada chamada a este \
                    endpoint realiza 3 chamadas reais e potencialmente pagas à API da OpenAI (transcrição, chat e \
                    geração de voz).

                    O arquivo é validado localmente (tamanho máximo de 10 MB e content type entre os aceitos) antes \
                    de qualquer chamada à OpenAI: um arquivo inválido nunca gera custo.

                    Requer o header obrigatório Idempotency-Key (string opaca gerada pelo cliente, até 128 \
                    caracteres, apenas letras/números/"._:-"). Reenviar a mesma chave com o mesmo arquivo nunca \
                    repete transcrição/ChatClient/Tool Calling: a resposta é reconstruída (novo TTS) a partir do \
                    texto já gerado. A mesma chave com um arquivo diferente, ou uma segunda requisição enquanto a \
                    primeira ainda processa, retorna 409.

                    A garantia de idempotência vale apenas dentro de uma janela de retenção configurável (padrão: \
                    24h para operações concluídas ou falhadas; 15 min de inatividade para considerar uma operação \
                    em processamento como abandonada). Após a janela, o registro é removido automaticamente e a \
                    mesma chave pode voltar a ser aceita como se fosse uma operação nova - reenviar o mesmo comando \
                    depois de expirado pode gerar uma nova transação. Use uma chave nova por intenção de comando; \
                    nunca reutilize propositalmente uma chave antiga."""
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Áudio MP3 com a resposta do assistente financeiro",
                    content = @Content(mediaType = "audio/mp3", schema = @Schema(type = "string", format = "binary"))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Parte 'file' ausente, requisição não multipart, arquivo vazio ou content type "
                            + "não permitido.",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Arquivo de áudio inválido",
                                    value = """
                                            {
                                              "type": "about:blank",
                                              "title": "Arquivo de áudio inválido",
                                              "status": 400,
                                              "detail": "O tipo do arquivo de áudio não é permitido.",
                                              "instance": "/transactions/ai",
                                              "timestamp": "2026-07-24T18:00:00-03:00"
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "Arquivo acima do tamanho máximo permitido (10 MB).",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Arquivo muito grande",
                                    value = """
                                            {
                                              "type": "about:blank",
                                              "title": "Arquivo muito grande",
                                              "status": 413,
                                              "detail": "O arquivo enviado excede o tamanho máximo permitido.",
                                              "instance": "/transactions/ai",
                                              "timestamp": "2026-07-24T18:00:00-03:00"
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito de idempotência: mesma chave usada com outro arquivo, operação ainda "
                            + "em processamento, ou falha anterior que impede reprocessamento seguro.",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Chave idempotente em conflito",
                                            value = """
                                                    {
                                                      "type": "about:blank",
                                                      "title": "Chave idempotente em conflito",
                                                      "status": 409,
                                                      "detail": "A chave de idempotência já foi utilizada com outro arquivo.",
                                                      "instance": "/transactions/ai",
                                                      "timestamp": "2026-07-24T18:00:00-03:00"
                                                    }"""
                                    ),
                                    @ExampleObject(
                                            name = "Operação em processamento",
                                            value = """
                                                    {
                                                      "type": "about:blank",
                                                      "title": "Operação em processamento",
                                                      "status": 409,
                                                      "detail": "Uma operação com esta chave já está em processamento.",
                                                      "instance": "/transactions/ai",
                                                      "timestamp": "2026-07-24T18:00:00-03:00"
                                                    }"""
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Áudio recebido e válido, mas sem conteúdo de fala identificável na transcrição.",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Áudio não processável",
                                    value = """
                                            {
                                              "type": "about:blank",
                                              "title": "Áudio não processável",
                                              "status": 422,
                                              "detail": "Não foi possível identificar conteúdo falado no áudio.",
                                              "instance": "/transactions/ai",
                                              "timestamp": "2026-07-24T18:00:00-03:00"
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Resposta inválida (vazia ou malformada) do ChatClient/TTS, ou falha genérica não "
                            + "classificada na integração com a OpenAI.",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Resposta inválida do serviço de IA",
                                    value = """
                                            {
                                              "type": "about:blank",
                                              "title": "Resposta inválida do serviço de IA",
                                              "status": 502,
                                              "detail": "O serviço de inteligência artificial retornou uma resposta inválida.",
                                              "instance": "/transactions/ai",
                                              "timestamp": "2026-07-24T18:00:00-03:00"
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Serviço de IA indisponível ou limite de requisições atingido no provedor. Nunca "
                            + "repassado como 429 do próprio endpoint, já que o limite é do provedor, não da API.",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Serviço de IA temporariamente indisponível",
                                    value = """
                                            {
                                              "type": "about:blank",
                                              "title": "Serviço de IA temporariamente indisponível",
                                              "status": 503,
                                              "detail": "O serviço de inteligência artificial está temporariamente indisponível. Tente novamente mais tarde.",
                                              "instance": "/transactions/ai",
                                              "timestamp": "2026-07-24T18:00:00-03:00"
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "O provedor de IA (transcrição, chat ou geração de voz) demorou mais que o tempo "
                            + "limite configurado para responder.",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Tempo limite excedido",
                                    value = """
                                            {
                                              "type": "about:blank",
                                              "title": "Tempo limite excedido",
                                              "status": 504,
                                              "detail": "O serviço de inteligência artificial demorou mais que o esperado para responder.",
                                              "instance": "/transactions/ai",
                                              "timestamp": "2026-07-24T18:00:00-03:00"
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro inesperado, incluindo falha de configuração/credencial da OpenAI (nunca "
                            + "exposta ao cliente).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "audio/mp3")
    ResponseEntity<Resource> transcribe(
            @Parameter(
                    description = "Chave opaca gerada pelo cliente para evitar duplicação por reenvio/retry/"
                            + "conexão perdida. Obrigatória, até 128 caracteres, apenas letras, números e '._:-'. "
                            + "Deve ser reutilizada somente para o mesmo comando de voz (mesmo arquivo).",
                    required = true,
                    example = "3f18cfbe-6072-4e5c-b7a6-183ae1809846"
            )
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(
                    description = "Arquivo de áudio com o comando financeiro falado. Obrigatório, até 10 MB, "
                            + "content type entre: audio/mpeg, audio/mp3, audio/mp4, audio/m4a, audio/x-m4a, "
                            + "audio/wav, audio/x-wav, audio/webm. Validado localmente antes de qualquer chamada à "
                            + "OpenAI. Os arquivos de teste do projeto usam .m4a; a validação é feita pelo header "
                            + "content type declarado pelo cliente, não pela inspeção do conteúdo binário.",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("file") MultipartFile file) throws IOException {
        audioFileValidator.validate(file);

        byte[] audioBytes = file.getBytes();
        String fingerprint = AudioPayloadFingerprint.of(audioBytes);
        var decision = idempotencyService.begin(idempotencyKey, fingerprint);

        byte[] responseAudio;
        boolean replayed = decision instanceof IdempotencyDecision.Replay;
        if (decision instanceof IdempotencyDecision.Replay replay) {
            responseAudio = aiTransactionProcessor.regenerateAudio(replay.responseText());
        } else {
            var start = (IdempotencyDecision.Start) decision;
            try {
                var result = aiTransactionProcessor.process(new ByteArrayResource(audioBytes),
                        stage -> idempotencyService.markStage(start.operationId(), stage));
                idempotencyService.complete(start.operationId(), result.responseText(), result.transactionId());
                responseAudio = result.audio();
            }
            catch (AiIntegrationException ex) {
                idempotencyService.fail(start.operationId(), ex.getStage());
                throw ex;
            }
        }

        var resource = new ByteArrayResource(responseAudio);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("audio.mp3")
                                .build()
                                .toString())
                .header("Idempotency-Replayed", String.valueOf(replayed))
                .body(resource);
    }
}

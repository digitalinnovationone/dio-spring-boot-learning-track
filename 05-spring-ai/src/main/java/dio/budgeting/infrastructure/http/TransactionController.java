package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Cadastro e consulta de transações financeiras, via REST tradicional ou comando de voz (transcrição + IA + Tool Calling + texto-para-voz)")
public class TransactionController {
    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private final TextToSpeechModel textToSpeechModel;

    public TransactionController(PersistTransactionUseCase persistTransactionUseCase,
                                 ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                 TranscriptionModel transcriptionModel,
                                 @Value("classpath:prompts/system-message.st") Resource systemPrompt,
                                 ChatClient.Builder chatClientBuilder,
                                 TextToSpeechModel textToSpeechModel) throws IOException {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(Charset.defaultCharset()))
                .defaultTools(persistTransactionUseCase, listTransactionsByCategoryUseCase)
                .build();
        this.textToSpeechModel = textToSpeechModel;
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
                                              "amount": 80.0
                                            }"""
                            )
                    )
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@RequestBody TransactionRequest request) {
        var transaction = persistTransactionUseCase.execute(request.toInput());
        return TransactionResponse.from(transaction);
    }

    @Operation(
            summary = "Lista transações por categoria",
            description = "Retorna todas as transações persistidas para a categoria informada. Retorna lista vazia quando não há registros."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Lista de transações da categoria (pode ser vazia)",
            content = @Content(schema = @Schema(implementation = TransactionResponse.class))
    )
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
                    geração de voz)."""
    )
    @ApiResponse(
            responseCode = "200",
            description = "Áudio MP3 com a resposta do assistente financeiro",
            content = @Content(mediaType = "audio/mp3", schema = @Schema(type = "string", format = "binary"))
    )
    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "audio/mp3")
    ResponseEntity<Resource> transcribe(
            @Parameter(
                    description = "Arquivo de áudio com o comando financeiro falado. O código não valida content-type "
                            + "nem formato: nenhum formato é tecnicamente imposto pela implementação atual. Os "
                            + "arquivos de teste do projeto usam .m4a; outros formatos aceitos pelo modelo whisper-1 "
                            + "da OpenAI são apenas recomendação para testes manuais, não uma garantia validada em código.",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("file") MultipartFile file) {
        var userMessage = transcriptionModel.transcribe(file.getResource());
        var result = chatClient.prompt().user(userMessage).call().content();

        byte[] audio = textToSpeechModel.call(result);
        var resource = new ByteArrayResource(audio);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("audio.mp3")
                                .build()
                                .toString())
                .body(resource);
    }
}

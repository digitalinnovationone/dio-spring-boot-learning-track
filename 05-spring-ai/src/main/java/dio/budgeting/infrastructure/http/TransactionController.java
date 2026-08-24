package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.RegisterAudioRecordUseCase;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import dio.budgeting.infrastructure.security.AuthenticatedUser;
import dio.budgeting.infrastructure.storage.AudioRequestContextHolder;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final RegisterAudioRecordUseCase registerAudioRecordUseCase;

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private final TextToSpeechModel textToSpeechModel;

    public TransactionController(PersistTransactionUseCase persistTransactionUseCase,
                                 ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                 RegisterAudioRecordUseCase registerAudioRecordUseCase,
                                 TranscriptionModel transcriptionModel,
                                 @Value("classpath:prompts/system-message.st") Resource systemPrompt,
                                 ChatClient.Builder chatClientBuilder,
                                 TextToSpeechModel textToSpeechModel) throws IOException {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.registerAudioRecordUseCase = registerAudioRecordUseCase;
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(Charset.defaultCharset()))
                .defaultTools(persistTransactionUseCase, listTransactionsByCategoryUseCase)
                .build();
        this.textToSpeechModel = textToSpeechModel;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(
            @RequestBody TransactionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        var transaction = persistTransactionUseCase.execute(request.toInput(), user.getId());
        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{category}")
    public List<TransactionResponse> readTransactions(
            @PathVariable Category category,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return listTransactionsByCategoryUseCase.execute(category, user.getId())
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "audio/mp3")
    public ResponseEntity<Resource> transcribe(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) throws IOException {

        var audioRecord = registerAudioRecordUseCase.execute(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType(),
                user.getId()
        );

        try {
            AudioRequestContextHolder.set(audioRecord.getId());

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
                    .header("X-Audio-Record-Id", audioRecord.getId().uuid().toString())
                    .body(resource);
        } finally {
            AudioRequestContextHolder.clear();
        }
    }
}

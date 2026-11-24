package dio.budgeting.infrastructure.http;

import dio.budgeting.application.BudgetAssistantUseCase;
import dio.budgeting.application.InvalidAudioException;
import dio.budgeting.application.output.AssistantOutput;
import dio.budgeting.infrastructure.http.request.AssistantTextRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/transactions/ai")
public class AssistantController {
    static final String AUDIO_MPEG = "audio/mpeg";

    private final BudgetAssistantUseCase budgetAssistantUseCase;

    public AssistantController(BudgetAssistantUseCase budgetAssistantUseCase) {
        this.budgetAssistantUseCase = budgetAssistantUseCase;
    }

    /** Fluxo completo: áudio de entrada → áudio MP3 de resposta. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = AUDIO_MPEG)
    public ResponseEntity<Resource> answerWithAudio(@RequestParam("file") MultipartFile file) {
        var output = budgetAssistantUseCase.answerAudio(audioOf(file));
        var audio = budgetAssistantUseCase.speak(output.answer());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(AUDIO_MPEG))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("audio.mp3").build().toString())
                .body(new ByteArrayResource(audio));
    }

    /** Áudio de entrada → JSON com a transcrição e a resposta em texto (sem gerar voz). */
    @PostMapping(value = "/text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AssistantOutput answerAudioWithText(@RequestParam("file") MultipartFile file) {
        return budgetAssistantUseCase.answerAudio(audioOf(file));
    }

    /** Texto de entrada → JSON com a resposta. Permite testar o tool calling sem gravar áudio. */
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AssistantOutput chat(@RequestBody AssistantTextRequest request) {
        return budgetAssistantUseCase.answerText(request.message());
    }

    private static Resource audioOf(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidAudioException("O arquivo de áudio está vazio");
        }
        return file.getResource();
    }
}

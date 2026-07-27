package dio.budgeting;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.ai.AiIntegrationException;
import dio.budgeting.infrastructure.http.audio.AudioFileValidator;
import dio.budgeting.infrastructure.http.audio.InvalidAudioFileException;
import dio.budgeting.infrastructure.idempotency.AudioCommandIdempotencyService;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationStore;
import dio.budgeting.infrastructure.idempotency.IdempotencyDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code POST /transactions/ai} always validates the upload
 * via {@link AudioFileValidator} before any call to
 * TranscriptionModel/ChatClient/TextToSpeechModel. Same isolation strategy
 * as {@link GlobalExceptionHandlerTest}: no MySQL/Docker, TranscriptionModel
 * and TextToSpeechModel mocked, no real OpenAI call. ChatClient is never
 * reached in these tests because TranscriptionModel either isn't called
 * (invalid upload) or is stubbed to fail immediately (valid upload), so the
 * real, auto-configured ChatClient.Builder never performs network I/O.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.ai.openai.api-key=test-key-not-a-real-credential",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
        }
)
@AutoConfigureMockMvc
class TransactionAudioUploadTest {

    @MockitoBean
    TransactionRepository transactionRepository;

    @MockitoBean
    PersistTransactionUseCase persistTransactionUseCase;

    @MockitoBean
    ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;

    @MockitoBean
    AudioFileValidator audioFileValidator;

    @MockitoBean
    TranscriptionModel transcriptionModel;

    @MockitoBean
    TextToSpeechModel textToSpeechModel;

    @MockitoBean
    AudioCommandIdempotencyService idempotencyService;

    // Unused directly, but a real @Component that needs the JPA repository this
    // test's context excludes - must be mocked too so the context can start.
    @MockitoBean
    AudioCommandOperationStore audioCommandOperationStore;

    @Autowired
    MockMvc mockMvc;

    private static MockMultipartFile audioFile() {
        return new MockMultipartFile("file", "audio.mp3", "audio/mpeg", new byte[]{1, 2, 3});
    }

    @BeforeEach
    void stubIdempotencyAsNewOperation() {
        // AudioCommandIdempotencyService is fully mocked here (real one needs JPA,
        // excluded in this context); real validation/persistence logic is covered
        // separately in AudioCommandIdempotencyServiceTest. Every test in this class
        // is a "new key" scenario as far as idempotency is concerned.
        when(idempotencyService.begin(any(), any()))
                .thenReturn(new IdempotencyDecision.Start(UUID.randomUUID()));
    }

    @Test
    void validFile_passesThroughValidator_andReachesTranscription() throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        when(transcriptionModel.transcribe(any())).thenThrow(new RuntimeException("simulated transcription failure"));

        // 502, not 500: AiTransactionProcessor (TASK-008) classifies unrecognized
        // transcription failures as a generic AI integration failure, not an internal bug.
        mockMvc.perform(multipart("/transactions/ai").file(audioFile())
                        .header("Idempotency-Key", "test-key-1"))
                .andExpect(status().isBadGateway());

        verify(audioFileValidator).validate(any());
        verify(transcriptionModel).transcribe(any());
        verifyNoInteractions(textToSpeechModel);
        verify(idempotencyService).fail(any(), org.mockito.ArgumentMatchers.eq(AiIntegrationException.Stage.TRANSCRIPTION));
    }

    @Test
    void emptyFile_isRejectedBeforeAnyExternalCall() throws Exception {
        doThrow(new InvalidAudioFileException(InvalidAudioFileException.Reason.EMPTY,
                "O arquivo de áudio não pode estar vazio."))
                .when(audioFileValidator).validate(any());

        mockMvc.perform(multipart("/transactions/ai").file(audioFile()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Arquivo de áudio inválido"))
                .andExpect(jsonPath("$.detail").value("O arquivo de áudio não pode estar vazio."))
                .andExpect(jsonPath("$.instance").value("/transactions/ai"));

        verifyNoInteractions(transcriptionModel);
        verifyNoInteractions(textToSpeechModel);
    }

    @Test
    void unsupportedContentType_isRejectedBeforeAnyExternalCall() throws Exception {
        doThrow(new InvalidAudioFileException(InvalidAudioFileException.Reason.UNSUPPORTED_TYPE,
                "O tipo do arquivo de áudio não é permitido."))
                .when(audioFileValidator).validate(any());

        mockMvc.perform(multipart("/transactions/ai").file(audioFile()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Arquivo de áudio inválido"))
                .andExpect(jsonPath("$.detail").value("O tipo do arquivo de áudio não é permitido."));

        verifyNoInteractions(transcriptionModel);
        verifyNoInteractions(textToSpeechModel);
    }

    @Test
    void fileAboveLocalSizeLimit_returns413_beforeAnyExternalCall() throws Exception {
        doThrow(new InvalidAudioFileException(InvalidAudioFileException.Reason.TOO_LARGE,
                "O arquivo de áudio excede o tamanho máximo permitido."))
                .when(audioFileValidator).validate(any());

        mockMvc.perform(multipart("/transactions/ai").file(audioFile()))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.title").value("Arquivo muito grande"))
                .andExpect(jsonPath("$.detail").value("O arquivo de áudio excede o tamanho máximo permitido."));

        verifyNoInteractions(transcriptionModel);
        verifyNoInteractions(textToSpeechModel);
    }
}

package dio.budgeting;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.ai.AiIntegrationException;
import dio.budgeting.infrastructure.ai.AiTransactionProcessor;
import dio.budgeting.infrastructure.ai.AiTransactionResult;
import dio.budgeting.infrastructure.http.audio.AudioFileValidator;
import dio.budgeting.infrastructure.http.audio.InvalidAudioFileException;
import dio.budgeting.infrastructure.idempotency.AudioCommandIdempotencyService;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationStore;
import dio.budgeting.infrastructure.idempotency.IdempotencyDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.stream.Stream;

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
 * Verifies that POST /transactions/ai maps every {@link AiIntegrationException}
 * reason (TASK-008) to the standardized ProblemDetail contract via
 * GlobalExceptionHandler. AiTransactionProcessor is mocked as a black box here
 * (its internal transcription/chat/speech ordering and classification logic
 * are covered by AiTransactionProcessorTest); no real OpenAI call happens.
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
class OpenAiFailureHandlingTest {

    @MockitoBean
    TransactionRepository transactionRepository;

    @MockitoBean
    PersistTransactionUseCase persistTransactionUseCase;

    @MockitoBean
    ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;

    @MockitoBean
    AudioFileValidator audioFileValidator;

    @MockitoBean
    AiTransactionProcessor aiTransactionProcessor;

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
        when(idempotencyService.begin(any(), any()))
                .thenReturn(new IdempotencyDecision.Start(UUID.randomUUID()));
    }

    static Stream<Arguments> failureReasons() {
        return Stream.of(
                Arguments.of(AiIntegrationException.Reason.TRANSCRIPTION_EMPTY, 422, "Áudio não processável"),
                Arguments.of(AiIntegrationException.Reason.TIMEOUT, 504, "Tempo limite excedido"),
                Arguments.of(AiIntegrationException.Reason.RATE_LIMITED, 503, "Serviço de IA temporariamente indisponível"),
                Arguments.of(AiIntegrationException.Reason.PROVIDER_UNAVAILABLE, 503, "Serviço de IA temporariamente indisponível"),
                Arguments.of(AiIntegrationException.Reason.INVALID_PROVIDER_RESPONSE, 502, "Resposta inválida do serviço de IA"),
                Arguments.of(AiIntegrationException.Reason.GENERIC_FAILURE, 502, "Falha na integração com IA"),
                Arguments.of(AiIntegrationException.Reason.CONFIGURATION_ERROR, 500, "Erro interno")
        );
    }

    @ParameterizedTest
    @MethodSource("failureReasons")
    void aiIntegrationFailure_mapsToStandardizedProblemDetail(AiIntegrationException.Reason reason,
                                                                int expectedStatus, String expectedTitle) throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        when(aiTransactionProcessor.process(any(), any()))
                .thenThrow(new AiIntegrationException(AiIntegrationException.Stage.CHAT, reason, "detail for " + reason));

        mockMvc.perform(multipart("/transactions/ai").file(audioFile()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.instance").value("/transactions/ai"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(idempotencyService).fail(any(), org.mockito.ArgumentMatchers.eq(AiIntegrationException.Stage.CHAT));
    }

    @Test
    void aiIntegrationFailure_neverLeaksExceptionMessageOrStackTrace() throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        when(aiTransactionProcessor.process(any(), any())).thenThrow(new AiIntegrationException(
                AiIntegrationException.Stage.TRANSCRIPTION, AiIntegrationException.Reason.CONFIGURATION_ERROR,
                "O serviço de inteligência artificial não está disponível no momento.",
                new RuntimeException("Invalid API key provided: sk-test-sensitive")));

        var body = mockMvc.perform(multipart("/transactions/ai").file(audioFile()))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("sk-test-sensitive")
                .doesNotContain("Invalid API key")
                .doesNotContain("RuntimeException")
                .doesNotContain("at dio.budgeting");
    }

    @Test
    void invalidAudioFile_isRejectedBeforeReachingProcessor() throws Exception {
        doThrow(new InvalidAudioFileException(InvalidAudioFileException.Reason.UNSUPPORTED_TYPE,
                "O tipo do arquivo de áudio não é permitido."))
                .when(audioFileValidator).validate(any());

        mockMvc.perform(multipart("/transactions/ai").file(audioFile()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiTransactionProcessor);
    }

    @Test
    void success_returnsAudioMp3() throws Exception {
        doNothing().when(audioFileValidator).validate(any());
        when(aiTransactionProcessor.process(any(), any()))
                .thenReturn(new AiTransactionResult(new byte[]{1, 2, 3}, "Transação registrada.", null));

        mockMvc.perform(multipart("/transactions/ai").file(audioFile()))
                .andExpect(status().isOk());

        verify(audioFileValidator).validate(any());
        verify(aiTransactionProcessor).process(any(), any());
        verify(idempotencyService).complete(any(), org.mockito.ArgumentMatchers.eq("Transação registrada."), any());
    }
}

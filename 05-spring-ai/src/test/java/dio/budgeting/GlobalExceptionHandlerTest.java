package dio.budgeting;

import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.domain.InvalidTransactionException;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.idempotency.AudioCommandIdempotencyService;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationStore;
import dio.budgeting.infrastructure.http.audio.InvalidAudioFileException;
import dio.budgeting.infrastructure.http.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the standardized ProblemDetail contract produced by
 * {@link dio.budgeting.infrastructure.http.error.GlobalExceptionHandler}.
 * Same isolation strategy as the other MockMvc tests in this module: no
 * MySQL/Docker, TransactionRepository mocked, placeholder OpenAI API key,
 * no real call to TranscriptionModel/ChatClient/TextToSpeechModel.
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
class GlobalExceptionHandlerTest {

    @MockitoBean
    TransactionRepository transactionRepository;

    @MockitoBean
    PersistTransactionUseCase persistTransactionUseCase;

    @MockitoBean
    ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;

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

    // --- InvalidTransactionException (domain) ---

    @Test
    void invalidTransactionException_isTranslatedTo400WithStandardContract() throws Exception {
        when(persistTransactionUseCase.execute(any()))
                .thenThrow(new InvalidTransactionException("O valor da transação deve ser maior que zero."));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Transação inválida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("O valor da transação deve ser maior que zero."))
                .andExpect(jsonPath("$.instance").value("/transactions"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void errorResponseBody_neverContainsStackTraceOrClassNames() throws Exception {
        when(persistTransactionUseCase.execute(any()))
                .thenThrow(new InvalidTransactionException("A categoria da transação é obrigatória."));

        var body = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("Exception")
                .doesNotContain("at dio.budgeting")
                .doesNotContain("InvalidTransactionException");
    }

    // --- Bean Validation ---

    @Test
    void beanValidationFailure_returnsAllFieldErrors_sortedByField() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "",
                                  "amount": 0,
                                  "category": null
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.detail").value("Um ou mais campos possuem valores inválidos."))
                .andExpect(jsonPath("$.instance").value("/transactions"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[1].field").value("category"))
                .andExpect(jsonPath("$.errors[2].field").value("description"));

        verifyNoInteractions(persistTransactionUseCase);
    }

    // --- JSON / enum ---

    @Test
    void malformedJson_returns400WithGenericMessage() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("O corpo da requisição está ausente ou possui formato inválido."));

        verifyNoInteractions(persistTransactionUseCase);
    }

    @Test
    void nonNumericAmount_returns400WithGenericMessage() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": "oitenta reais",
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));

        verifyNoInteractions(persistTransactionUseCase);
    }

    @Test
    void invalidCategoryInBody_returns400WithAcceptedValues() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                  "category": "FOOD"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value("Categoria inválida. Valores aceitos: GROCERIES, PHARMA, AUTO."));

        verifyNoInteractions(persistTransactionUseCase);
    }

    @Test
    void invalidCategoryInRoute_returns400WithAcceptedValues() throws Exception {
        mockMvc.perform(get("/transactions/FOOD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parâmetro inválido"))
                .andExpect(jsonPath("$.detail").value(
                        "O valor 'FOOD' não é válido para o parâmetro 'category'. Valores aceitos: GROCERIES, PHARMA, AUTO."))
                .andExpect(jsonPath("$.instance").value("/transactions/FOOD"));

        verifyNoInteractions(listTransactionsByCategoryUseCase);
    }

    // --- Multipart ---

    @Test
    void missingAudioFile_returns400_andNeverCallsAiPipeline() throws Exception {
        mockMvc.perform(multipart("/transactions/ai"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Arquivo obrigatório"))
                .andExpect(jsonPath("$.detail").value("O arquivo de áudio é obrigatório."))
                .andExpect(jsonPath("$.instance").value("/transactions/ai"));

        verifyNoInteractions(transcriptionModel);
        verifyNoInteractions(textToSpeechModel);
    }

    // --- Unexpected error ---

    @Test
    void unexpectedException_returns500_withoutLeakingInternalDetails() throws Exception {
        when(persistTransactionUseCase.execute(any()))
                .thenThrow(new RuntimeException("detalhe interno sensível"));

        var body = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Combustível",
                                  "amount": 80.90,
                                  "category": "AUTO"
                                }"""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado ao processar a requisição."))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("detalhe interno sensível")
                .doesNotContain("RuntimeException");
    }

    // --- Audio upload validation (handler mapping, unit-level: no MockMvc/context needed) ---

    @Test
    void invalidAudioFileException_emptyReason_returns400() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleInvalidAudioFile(
                new InvalidAudioFileException(InvalidAudioFileException.Reason.EMPTY,
                        "O arquivo de áudio não pode estar vazio."),
                request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Arquivo de áudio inválido");
        assertThat(problem.getDetail()).isEqualTo("O arquivo de áudio não pode estar vazio.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/transactions/ai"));
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void invalidAudioFileException_unsupportedTypeReason_returns400() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleInvalidAudioFile(
                new InvalidAudioFileException(InvalidAudioFileException.Reason.UNSUPPORTED_TYPE,
                        "O tipo do arquivo de áudio não é permitido."),
                request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Arquivo de áudio inválido");
        assertThat(problem.getDetail()).isEqualTo("O tipo do arquivo de áudio não é permitido.");
    }

    @Test
    void invalidAudioFileException_tooLargeReason_returns413() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleInvalidAudioFile(
                new InvalidAudioFileException(InvalidAudioFileException.Reason.TOO_LARGE,
                        "O arquivo de áudio excede o tamanho máximo permitido."),
                request);

        assertThat(problem.getStatus()).isEqualTo(413);
        assertThat(problem.getTitle()).isEqualTo("Arquivo muito grande");
        assertThat(problem.getDetail()).isEqualTo("O arquivo de áudio excede o tamanho máximo permitido.");
    }

    @Test
    void maxUploadSizeExceededException_returns413_withGenericMessage() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(10_485_760), request);

        assertThat(problem.getStatus()).isEqualTo(413);
        assertThat(problem.getTitle()).isEqualTo("Arquivo muito grande");
        assertThat(problem.getDetail()).isEqualTo("O arquivo enviado excede o tamanho máximo permitido.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/transactions/ai"));
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void malformedMultipartRequest_returns400_withGenericMessage() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleMultipartException(new MultipartException("boom"), request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Requisição inválida");
        assertThat(problem.getDetail()).isEqualTo("A requisição multipart está ausente ou possui formato inválido.");
    }

    // --- AI integration failures (TASK-008, handler mapping: no MockMvc/context needed) ---

    @Test
    void aiIntegrationException_transcriptionEmpty_returns422() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleAiIntegrationFailure(new dio.budgeting.infrastructure.ai.AiIntegrationException(
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Stage.TRANSCRIPTION,
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Reason.TRANSCRIPTION_EMPTY,
                        "Não foi possível identificar conteúdo falado no áudio."),
                request);

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("Áudio não processável");
        assertThat(problem.getDetail()).isEqualTo("Não foi possível identificar conteúdo falado no áudio.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/transactions/ai"));
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void aiIntegrationException_timeout_returns504() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleAiIntegrationFailure(new dio.budgeting.infrastructure.ai.AiIntegrationException(
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Stage.CHAT,
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Reason.TIMEOUT,
                        "O serviço de inteligência artificial demorou mais que o esperado para responder."),
                request);

        assertThat(problem.getStatus()).isEqualTo(504);
        assertThat(problem.getTitle()).isEqualTo("Tempo limite excedido");
    }

    @Test
    void aiIntegrationException_rateLimited_returns503_notRaw429() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleAiIntegrationFailure(new dio.budgeting.infrastructure.ai.AiIntegrationException(
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Stage.TRANSCRIPTION,
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Reason.RATE_LIMITED,
                        "O serviço de inteligência artificial está temporariamente indisponível. Tente novamente mais tarde."),
                request);

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getTitle()).isEqualTo("Serviço de IA temporariamente indisponível");
    }

    @Test
    void aiIntegrationException_providerUnavailable_returns503() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleAiIntegrationFailure(new dio.budgeting.infrastructure.ai.AiIntegrationException(
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Stage.SPEECH,
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Reason.PROVIDER_UNAVAILABLE,
                        "O serviço de inteligência artificial está temporariamente indisponível. Tente novamente mais tarde."),
                request);

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getTitle()).isEqualTo("Serviço de IA temporariamente indisponível");
    }

    @Test
    void aiIntegrationException_invalidProviderResponse_returns502() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleAiIntegrationFailure(new dio.budgeting.infrastructure.ai.AiIntegrationException(
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Stage.SPEECH,
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Reason.INVALID_PROVIDER_RESPONSE,
                        "Não foi possível gerar a resposta em áudio."),
                request);

        assertThat(problem.getStatus()).isEqualTo(502);
        assertThat(problem.getTitle()).isEqualTo("Resposta inválida do serviço de IA");
    }

    @Test
    void aiIntegrationException_genericFailure_returns502() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleAiIntegrationFailure(new dio.budgeting.infrastructure.ai.AiIntegrationException(
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Stage.TRANSCRIPTION,
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Reason.GENERIC_FAILURE,
                        "Falha na integração com o serviço de inteligência artificial."),
                request);

        assertThat(problem.getStatus()).isEqualTo(502);
        assertThat(problem.getTitle()).isEqualTo("Falha na integração com IA");
    }

    @Test
    void aiIntegrationException_configurationError_returns500_withoutLeakingCredentialDetails() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleAiIntegrationFailure(new dio.budgeting.infrastructure.ai.AiIntegrationException(
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Stage.TRANSCRIPTION,
                        dio.budgeting.infrastructure.ai.AiIntegrationException.Reason.CONFIGURATION_ERROR,
                        "O serviço de inteligência artificial não está disponível no momento.",
                        new RuntimeException("Invalid API key provided: sk-test-sensitive")),
                request);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Erro interno");
        assertThat(problem.getDetail()).isEqualTo("O serviço de inteligência artificial não está disponível no momento.");
        assertThat(problem.getDetail()).doesNotContain("sk-test-sensitive", "API key", "RuntimeException");
    }

    // --- Idempotency failures (TASK-009, handler mapping: no MockMvc/context needed) ---

    @Test
    void idempotencyException_missingKey_returns400() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleIdempotencyFailure(new dio.budgeting.infrastructure.idempotency.IdempotencyException(
                        dio.budgeting.infrastructure.idempotency.IdempotencyException.Reason.MISSING_KEY,
                        "O header Idempotency-Key é obrigatório."),
                request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Chave idempotente ausente");
        assertThat(problem.getDetail()).isEqualTo("O header Idempotency-Key é obrigatório.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/transactions/ai"));
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void idempotencyException_invalidKey_returns400() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleIdempotencyFailure(new dio.budgeting.infrastructure.idempotency.IdempotencyException(
                        dio.budgeting.infrastructure.idempotency.IdempotencyException.Reason.INVALID_KEY,
                        "O header Idempotency-Key possui formato inválido."),
                request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Chave idempotente inválida");
    }

    @Test
    void idempotencyException_payloadConflict_returns409_withoutExposingFingerprint() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleIdempotencyFailure(new dio.budgeting.infrastructure.idempotency.IdempotencyException(
                        dio.budgeting.infrastructure.idempotency.IdempotencyException.Reason.PAYLOAD_CONFLICT,
                        "A chave de idempotência já foi utilizada com outro arquivo."),
                request);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Chave idempotente em conflito");
        assertThat(problem.getDetail()).doesNotContainPattern("[0-9a-f]{64}");
    }

    @Test
    void idempotencyException_inProgress_returns409() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleIdempotencyFailure(new dio.budgeting.infrastructure.idempotency.IdempotencyException(
                        dio.budgeting.infrastructure.idempotency.IdempotencyException.Reason.IN_PROGRESS,
                        "Uma operação com esta chave já está em processamento."),
                request);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Operação em processamento");
    }

    @Test
    void idempotencyException_retryNotAllowed_returns409() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/transactions/ai");

        var problem = handler.handleIdempotencyFailure(new dio.budgeting.infrastructure.idempotency.IdempotencyException(
                        dio.budgeting.infrastructure.idempotency.IdempotencyException.Reason.RETRY_NOT_ALLOWED,
                        "Esta chave de idempotência falhou em uma etapa que pode já ter persistido uma transação. "
                                + "Utilize uma nova chave para tentar novamente."),
                request);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Reprocessamento não permitido");
    }
}

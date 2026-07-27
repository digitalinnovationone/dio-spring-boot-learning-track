package dio.budgeting.infrastructure.ai;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.domain.InvalidTransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Consumer;

/**
 * Integration boundary between the controller and the three OpenAI-backed
 * models (transcription, chat/tool-calling, speech). Knows nothing about
 * {@code ResponseEntity}/{@code ProblemDetail}/HTTP status: failures are
 * reported as {@link AiIntegrationException}, classified by {@link
 * AiIntegrationException.Stage} and {@link AiIntegrationException.Reason},
 * for {@code GlobalExceptionHandler} to translate into HTTP responses.
 *
 * <p>Domain exceptions thrown by the persist/list tools during Tool Calling
 * (e.g. {@code InvalidTransactionException}) never reach this class as Java
 * exceptions: Spring AI's default {@code ToolExecutionExceptionProcessor}
 * converts any {@code RuntimeException} thrown by a {@code @Tool} method into
 * plain text sent back to the model as the tool's result, not a propagated
 * exception. So a rejected voice command surfaces as a normal (non-empty)
 * assistant reply, not as a failure here - this class only ever sees genuine
 * transport/provider failures from the three model calls themselves.
 */
@Component
public class AiTransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(AiTransactionProcessor.class);

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private final TextToSpeechModel textToSpeechModel;

    public AiTransactionProcessor(TranscriptionModel transcriptionModel,
                                   @Value("classpath:prompts/system-message.st") Resource systemPrompt,
                                   ChatClient.Builder chatClientBuilder,
                                   TextToSpeechModel textToSpeechModel,
                                   PersistTransactionUseCase persistTransactionUseCase,
                                   ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase)
            throws IOException {
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(Charset.defaultCharset()))
                .defaultTools(persistTransactionUseCase, listTransactionsByCategoryUseCase)
                .build();
        this.textToSpeechModel = textToSpeechModel;
    }

    public AiTransactionResult process(Resource audioResource) {
        return process(audioResource, stage -> { });
    }

    /**
     * Same pipeline as {@link #process(Resource)}, but reports the stage about
     * to run to {@code stageTracker} right before each external call - used by
     * the controller to persist {@code AudioCommandOperationEntity.currentStage}
     * (TASK-010), so an abandoned operation can later be classified as safe
     * (before Tool Calling) or unsafe (Tool Calling may have run) to retry.
     */
    public AiTransactionResult process(Resource audioResource, Consumer<AiIntegrationException.Stage> stageTracker) {
        stageTracker.accept(AiIntegrationException.Stage.TRANSCRIPTION);
        String transcript = transcribe(audioResource);
        stageTracker.accept(AiIntegrationException.Stage.CHAT);
        String reply = chat(transcript);
        stageTracker.accept(AiIntegrationException.Stage.SPEECH);
        byte[] audio = synthesize(reply);
        // transactionId is always null - see AiTransactionResult's javadoc.
        return new AiTransactionResult(audio, reply, null);
    }

    /**
     * Regenerates audio from an already-known, safe response text - used to
     * replay a COMPLETED idempotent operation without repeating transcription,
     * ChatClient or Tool Calling.
     */
    public byte[] regenerateAudio(String responseText) {
        return synthesize(responseText);
    }

    private String transcribe(Resource audioResource) {
        String text;
        try {
            text = transcriptionModel.transcribe(audioResource);
        }
        catch (RuntimeException ex) {
            throw classify(AiIntegrationException.Stage.TRANSCRIPTION, ex);
        }
        if (text == null || text.isBlank()) {
            log.warn("Falha na etapa de transcrição. reason={}", AiIntegrationException.Reason.TRANSCRIPTION_EMPTY);
            throw new AiIntegrationException(AiIntegrationException.Stage.TRANSCRIPTION,
                    AiIntegrationException.Reason.TRANSCRIPTION_EMPTY,
                    "Não foi possível identificar conteúdo falado no áudio.");
        }
        return text;
    }

    private String chat(String transcript) {
        String content;
        try {
            content = chatClient.prompt().user(transcript).call().content();
        }
        catch (InvalidTransactionException ex) {
            // Defense in depth: with the default ToolExecutionExceptionProcessor,
            // exceptions thrown by @Tool methods (persist-transaction/list-transactions)
            // are converted to plain tool-result text and never reach this catch block
            // as a propagated exception - verified in DefaultToolExecutionExceptionProcessor.
            // If that ever changes, a rejected user command must still surface as the
            // existing domain-error contract (400 Transação inválida), not as an AI failure.
            throw ex;
        }
        catch (RuntimeException ex) {
            throw classify(AiIntegrationException.Stage.CHAT, ex);
        }
        if (content == null || content.isBlank()) {
            log.warn("Falha na etapa de chat. reason={}", AiIntegrationException.Reason.INVALID_PROVIDER_RESPONSE);
            throw new AiIntegrationException(AiIntegrationException.Stage.CHAT,
                    AiIntegrationException.Reason.INVALID_PROVIDER_RESPONSE,
                    "A inteligência artificial não retornou uma resposta válida.");
        }
        return content;
    }

    private byte[] synthesize(String text) {
        byte[] audio;
        try {
            audio = textToSpeechModel.call(text);
        }
        catch (RuntimeException ex) {
            throw classify(AiIntegrationException.Stage.SPEECH, ex);
        }
        if (audio == null || audio.length == 0) {
            log.warn("Falha na etapa de geração de voz. reason={}", AiIntegrationException.Reason.INVALID_PROVIDER_RESPONSE);
            throw new AiIntegrationException(AiIntegrationException.Stage.SPEECH,
                    AiIntegrationException.Reason.INVALID_PROVIDER_RESPONSE,
                    "Não foi possível gerar a resposta em áudio.");
        }
        return audio;
    }

    /**
     * Walks the full cause chain instead of assuming a fixed nesting depth:
     * {@code OpenAiAudioTranscriptionModel} wraps failures in a generic
     * {@code RuntimeException} two levels deep, while {@code OpenAiChatModel}/
     * {@code OpenAiAudioSpeechModel} (via {@code RetryUtils.execute}) rethrow
     * the original typed exception directly.
     */
    private AiIntegrationException classify(AiIntegrationException.Stage stage, RuntimeException ex) {
        AiIntegrationException.Reason reason = AiIntegrationException.Reason.GENERIC_FAILURE;

        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResourceAccessException resourceAccessException) {
                reason = isTimeout(resourceAccessException.getCause())
                        ? AiIntegrationException.Reason.TIMEOUT
                        : AiIntegrationException.Reason.PROVIDER_UNAVAILABLE;
                break;
            }
            if (cause instanceof OpenAiClientException clientException) {
                reason = switch (clientException.getStatusCode()) {
                    case 401, 403 -> AiIntegrationException.Reason.CONFIGURATION_ERROR;
                    case 429 -> AiIntegrationException.Reason.RATE_LIMITED;
                    default -> AiIntegrationException.Reason.GENERIC_FAILURE;
                };
                break;
            }
            if (cause instanceof OpenAiServerException) {
                reason = AiIntegrationException.Reason.PROVIDER_UNAVAILABLE;
                break;
            }
            if (cause instanceof NonTransientAiException) {
                reason = AiIntegrationException.Reason.GENERIC_FAILURE;
                break;
            }
            if (cause instanceof TransientAiException) {
                reason = AiIntegrationException.Reason.PROVIDER_UNAVAILABLE;
                break;
            }
        }

        log.warn("Falha na etapa de integração com IA. stage={} reason={}", stage, reason, ex);
        return new AiIntegrationException(stage, reason, messageFor(reason), ex);
    }

    private static boolean isTimeout(Throwable cause) {
        return cause != null && cause.getClass().getSimpleName().endsWith("TimeoutException");
    }

    private static String messageFor(AiIntegrationException.Reason reason) {
        return switch (reason) {
            case TIMEOUT -> "O serviço de inteligência artificial demorou mais que o esperado para responder.";
            case RATE_LIMITED, PROVIDER_UNAVAILABLE ->
                    "O serviço de inteligência artificial está temporariamente indisponível. Tente novamente mais tarde.";
            case CONFIGURATION_ERROR -> "O serviço de inteligência artificial não está disponível no momento.";
            case INVALID_PROVIDER_RESPONSE -> "O serviço de inteligência artificial retornou uma resposta inválida.";
            case TRANSCRIPTION_EMPTY, GENERIC_FAILURE -> "Falha na integração com o serviço de inteligência artificial.";
        };
    }
}

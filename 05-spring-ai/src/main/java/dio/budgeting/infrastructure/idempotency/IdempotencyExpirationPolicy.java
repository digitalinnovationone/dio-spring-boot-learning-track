package dio.budgeting.infrastructure.idempotency;

import dio.budgeting.infrastructure.ai.AiIntegrationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Single source of truth for "is this operation still within its retention/
 * timeout window" (TASK-010). Both the scheduled cleanup ({@code
 * IdempotencyCleanupService}) and the opportunistic check inside {@code
 * AudioCommandIdempotencyService#begin} consult these same thresholds, so a
 * request never has to wait for the next scheduler run to see an abandoned or
 * expired operation resolved.
 *
 * <p>Threshold semantics are exclusive: {@code updatedAt} strictly before the
 * threshold is expired/abandoned; exactly at the threshold is still valid.
 */
@Component
public class IdempotencyExpirationPolicy {

    private final Clock clock;
    private final IdempotencyProperties properties;

    public IdempotencyExpirationPolicy(Clock clock, IdempotencyProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public OffsetDateTime completedExpirationThreshold() {
        return now().minus(properties.getCompletedRetention());
    }

    public OffsetDateTime failedExpirationThreshold() {
        return now().minus(properties.getFailedRetention());
    }

    public OffsetDateTime processingAbandonedThreshold() {
        return now().minus(properties.getProcessingTimeout());
    }

    public boolean isCompletedExpired(AudioCommandOperationEntity entity) {
        return entity.getUpdatedAt().isBefore(completedExpirationThreshold());
    }

    public boolean isFailedExpired(AudioCommandOperationEntity entity) {
        return entity.getUpdatedAt().isBefore(failedExpirationThreshold());
    }

    public boolean isProcessingAbandoned(AudioCommandOperationEntity entity) {
        return entity.getUpdatedAt().isBefore(processingAbandonedThreshold());
    }

    /**
     * The failureStage to record when converting an abandoned PROCESSING
     * operation to FAILED, reusing the existing retry policy in {@code
     * AudioCommandIdempotencyService#resolveFailed}: TRANSCRIPTION (or not yet
     * started) always allows retry; CHAT, SPEECH, and an unknown/null stage
     * never do - never presume no side effect happened.
     */
    public AiIntegrationException.Stage failureStageForAbandoned(AudioCommandOperationEntity entity) {
        var stage = entity.getCurrentStage();
        if (stage == AudioCommandStage.REGISTERED || stage == AudioCommandStage.TRANSCRIPTION) {
            return AiIntegrationException.Stage.TRANSCRIPTION;
        }
        if (stage == AudioCommandStage.SPEECH) {
            return AiIntegrationException.Stage.SPEECH;
        }
        // CHAT, or an unknown/null stage: never presume Tool Calling did not run.
        return AiIntegrationException.Stage.CHAT;
    }
}

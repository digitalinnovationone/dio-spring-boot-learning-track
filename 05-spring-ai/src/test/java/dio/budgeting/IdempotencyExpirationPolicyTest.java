package dio.budgeting;

import dio.budgeting.infrastructure.ai.AiIntegrationException;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationEntity;
import dio.budgeting.infrastructure.idempotency.AudioCommandStage;
import dio.budgeting.infrastructure.idempotency.IdempotencyExpirationPolicy;
import dio.budgeting.infrastructure.idempotency.IdempotencyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic tests for IdempotencyExpirationPolicy (TASK-010) using {@link
 * Clock#fixed}, no Spring context and no real time. The threshold is
 * exclusive: updatedAt strictly before it is expired/abandoned; exactly at
 * the threshold is still valid.
 */
class IdempotencyExpirationPolicyTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-27T12:00:00Z");

    private IdempotencyExpirationPolicy policy;

    @BeforeEach
    void setUp() {
        var properties = new IdempotencyProperties();
        properties.setCompletedRetention(Duration.ofHours(24));
        properties.setFailedRetention(Duration.ofHours(24));
        properties.setProcessingTimeout(Duration.ofMinutes(15));
        policy = new IdempotencyExpirationPolicy(Clock.fixed(FIXED_NOW, ZoneOffset.UTC), properties);
    }

    private static AudioCommandOperationEntity entityUpdatedAt(OffsetDateTime updatedAt) {
        return new AudioCommandOperationEntity("key", "fp", updatedAt);
    }

    // --- COMPLETED ---

    @Test
    void completed_withinRetention_isNotExpired() {
        assertThat(policy.isCompletedExpired(entityUpdatedAt(policy.now().minusHours(23)))).isFalse();
    }

    @Test
    void completed_exactlyAtLimit_isNotExpired() {
        assertThat(policy.isCompletedExpired(entityUpdatedAt(policy.completedExpirationThreshold()))).isFalse();
    }

    @Test
    void completed_pastLimit_isExpired() {
        assertThat(policy.isCompletedExpired(entityUpdatedAt(policy.completedExpirationThreshold().minusNanos(1)))).isTrue();
    }

    // --- FAILED ---

    @Test
    void failed_withinRetention_isNotExpired() {
        assertThat(policy.isFailedExpired(entityUpdatedAt(policy.now().minusHours(1)))).isFalse();
    }

    @Test
    void failed_exactlyAtLimit_isNotExpired() {
        assertThat(policy.isFailedExpired(entityUpdatedAt(policy.failedExpirationThreshold()))).isFalse();
    }

    @Test
    void failed_pastRetention_isExpired() {
        assertThat(policy.isFailedExpired(entityUpdatedAt(policy.failedExpirationThreshold().minusSeconds(1)))).isTrue();
    }

    // --- PROCESSING ---

    @Test
    void processing_recent_isNotAbandoned() {
        assertThat(policy.isProcessingAbandoned(entityUpdatedAt(policy.now().minusMinutes(1)))).isFalse();
    }

    @Test
    void processing_exactlyAtTimeout_isNotAbandoned() {
        assertThat(policy.isProcessingAbandoned(entityUpdatedAt(policy.processingAbandonedThreshold()))).isFalse();
    }

    @Test
    void processing_pastTimeout_isAbandoned() {
        assertThat(policy.isProcessingAbandoned(entityUpdatedAt(policy.processingAbandonedThreshold().minusNanos(1)))).isTrue();
    }

    // --- Recovery classification ---

    @Test
    void registeredStage_isSafeToRetry() {
        var entity = entityUpdatedAt(policy.now());
        entity.setCurrentStage(AudioCommandStage.REGISTERED);

        assertThat(policy.failureStageForAbandoned(entity)).isEqualTo(AiIntegrationException.Stage.TRANSCRIPTION);
    }

    @Test
    void transcriptionStage_isSafeToRetry() {
        var entity = entityUpdatedAt(policy.now());
        entity.setCurrentStage(AudioCommandStage.TRANSCRIPTION);

        assertThat(policy.failureStageForAbandoned(entity)).isEqualTo(AiIntegrationException.Stage.TRANSCRIPTION);
    }

    @Test
    void chatStage_isUnsafe() {
        var entity = entityUpdatedAt(policy.now());
        entity.setCurrentStage(AudioCommandStage.CHAT);

        assertThat(policy.failureStageForAbandoned(entity)).isEqualTo(AiIntegrationException.Stage.CHAT);
    }

    @Test
    void speechStage_isUnsafe() {
        var entity = entityUpdatedAt(policy.now());
        entity.setCurrentStage(AudioCommandStage.SPEECH);

        assertThat(policy.failureStageForAbandoned(entity)).isEqualTo(AiIntegrationException.Stage.SPEECH);
    }

    @Test
    void unknownNullStage_isTreatedAsUnsafe() {
        var entity = entityUpdatedAt(policy.now());
        entity.setCurrentStage(null);

        assertThat(policy.failureStageForAbandoned(entity)).isEqualTo(AiIntegrationException.Stage.CHAT);
    }
}

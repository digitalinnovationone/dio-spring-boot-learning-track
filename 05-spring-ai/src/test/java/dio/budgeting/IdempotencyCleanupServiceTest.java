package dio.budgeting;

import dio.budgeting.infrastructure.idempotency.AudioCommandIdempotencyService;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationEntity;
import dio.budgeting.infrastructure.idempotency.AudioCommandOperationRepository;
import dio.budgeting.infrastructure.idempotency.AudioCommandStage;
import dio.budgeting.infrastructure.idempotency.AudioCommandStatus;
import dio.budgeting.infrastructure.idempotency.IdempotencyCleanupService;
import dio.budgeting.infrastructure.idempotency.IdempotencyDecision;
import dio.budgeting.infrastructure.idempotency.IdempotencyExpirationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real JPA/H2 tests (TASK-010) for IdempotencyCleanupService: recovery of
 * abandoned PROCESSING rows and deletion of expired COMPLETED/FAILED rows,
 * against the same isolated in-memory database used by
 * AudioCommandIdempotencyServiceTest. Rows are backdated directly via
 * repository.save (bypassing AudioCommandOperationStore, exactly like
 * TransactionAudioIdempotencyTest already does for PROCESSING) using real
 * wall-clock arithmetic against IdempotencyExpirationPolicy's own thresholds -
 * no Thread.sleep, no fake Clock needed since only relative offsets matter.
 * No OpenAI call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class IdempotencyCleanupServiceTest {

    @Autowired
    IdempotencyCleanupService cleanupService;

    @Autowired
    IdempotencyExpirationPolicy policy;

    @Autowired
    AudioCommandOperationRepository repository;

    @Autowired
    AudioCommandIdempotencyService idempotencyService;

    private AudioCommandOperationEntity save(String key, AudioCommandStatus status, OffsetDateTime updatedAt) {
        var entity = new AudioCommandOperationEntity(key, "fp-" + key, updatedAt);
        entity.setStatus(status);
        entity.setUpdatedAt(updatedAt);
        if (status == AudioCommandStatus.FAILED) {
            entity.setFailureStage(dio.budgeting.infrastructure.ai.AiIntegrationException.Stage.CHAT);
        }
        if (status == AudioCommandStatus.COMPLETED) {
            entity.setResponseText("resposta");
        }
        return repository.save(entity);
    }

    // --- cleanupExpired: COMPLETED ---

    @Test
    void completedOld_isRemoved() {
        var entity = save("completed-old", AudioCommandStatus.COMPLETED,
                policy.completedExpirationThreshold().minusSeconds(1));

        int removed = cleanupService.cleanupExpired();

        assertThat(removed).isEqualTo(1);
        assertThat(repository.findById(entity.getId())).isEmpty();
    }

    @Test
    void completedRecent_remains() {
        var entity = save("completed-recent", AudioCommandStatus.COMPLETED, policy.now());

        int removed = cleanupService.cleanupExpired();

        assertThat(removed).isZero();
        assertThat(repository.findById(entity.getId())).isPresent();
    }

    // --- cleanupExpired: FAILED ---

    @Test
    void failedOld_isRemoved() {
        var entity = save("failed-old", AudioCommandStatus.FAILED,
                policy.failedExpirationThreshold().minusSeconds(1));

        int removed = cleanupService.cleanupExpired();

        assertThat(removed).isEqualTo(1);
        assertThat(repository.findById(entity.getId())).isEmpty();
    }

    @Test
    void failedRecent_remains() {
        var entity = save("failed-recent", AudioCommandStatus.FAILED, policy.now());

        int removed = cleanupService.cleanupExpired();

        assertThat(removed).isZero();
        assertThat(repository.findById(entity.getId())).isPresent();
    }

    // --- recoverAbandonedProcessing ---

    @Test
    void processingRecent_remainsUntouched() {
        var entity = save("processing-recent", AudioCommandStatus.PROCESSING, policy.now());

        int recovered = cleanupService.recoverAbandonedProcessing();

        assertThat(recovered).isZero();
        var fresh = repository.findById(entity.getId()).orElseThrow();
        assertThat(fresh.getStatus()).isEqualTo(AudioCommandStatus.PROCESSING);
    }

    @Test
    void processingAbandoned_isRecoveredNotRemoved() {
        var entity = save("processing-abandoned", AudioCommandStatus.PROCESSING,
                policy.processingAbandonedThreshold().minusSeconds(1));
        entity.setCurrentStage(AudioCommandStage.CHAT);
        repository.save(entity);

        int recovered = cleanupService.recoverAbandonedProcessing();
        int removed = cleanupService.cleanupExpired();

        assertThat(recovered).isEqualTo(1);
        assertThat(removed).isZero(); // freshly failed, not expired yet
        var fresh = repository.findById(entity.getId()).orElseThrow();
        assertThat(fresh.getStatus()).isEqualTo(AudioCommandStatus.FAILED);
        assertThat(fresh.getFailureStage()).isEqualTo(dio.budgeting.infrastructure.ai.AiIntegrationException.Stage.CHAT);
    }

    // --- Key reuse after removal ---

    @Test
    void keyIsReusable_afterExpiredOperationRemoved() {
        var content = "fp-reuse-key";
        var entity = new AudioCommandOperationEntity("reuse-key", content,
                policy.completedExpirationThreshold().minusSeconds(1));
        entity.setStatus(AudioCommandStatus.COMPLETED);
        entity.setResponseText("resposta antiga");
        entity.setUpdatedAt(policy.completedExpirationThreshold().minusSeconds(1));
        repository.save(entity);

        int removed = cleanupService.cleanupExpired();
        assertThat(removed).isEqualTo(1);

        var decision = idempotencyService.begin("reuse-key", content);
        assertThat(decision).isInstanceOf(IdempotencyDecision.Start.class);
    }
}

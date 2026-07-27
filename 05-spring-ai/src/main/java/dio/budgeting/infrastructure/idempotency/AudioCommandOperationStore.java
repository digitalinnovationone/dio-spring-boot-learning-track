package dio.budgeting.infrastructure.idempotency;

import dio.budgeting.infrastructure.ai.AiIntegrationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns every read/write of {@code audio_command_operation} in its own short
 * transaction. Kept as a separate bean (not methods on {@code
 * AudioCommandIdempotencyService}) so each {@code @Transactional} call goes
 * through Spring's proxy - a self-invocation from within the same class would
 * silently skip the proxy and the transaction. This keeps the (potentially
 * 60s+) OpenAI calls in AiTransactionProcessor fully outside any JPA
 * transaction: each of insertNew/complete/fail/restartForRetry/markStage is
 * its own commit, released immediately.
 *
 * <p>TASK-010: also backs recovery/cleanup (used by both {@code
 * IdempotencyCleanupService} and the opportunistic check in {@code
 * AudioCommandIdempotencyService#begin}), each as its own short,
 * DB-only transaction - never held open across an external call.
 */
@Component
public class AudioCommandOperationStore {

    private final AudioCommandOperationRepository repository;
    private final IdempotencyExpirationPolicy policy;
    private final Clock clock;

    AudioCommandOperationStore(AudioCommandOperationRepository repository, IdempotencyExpirationPolicy policy,
                                Clock clock) {
        this.repository = repository;
        this.policy = policy;
        this.clock = clock;
    }

    Optional<AudioCommandOperationEntity> findByKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional
    AudioCommandOperationEntity insertNew(String idempotencyKey, String payloadFingerprint) {
        return repository.save(new AudioCommandOperationEntity(idempotencyKey, payloadFingerprint, OffsetDateTime.now(clock)));
    }

    @Transactional
    AudioCommandOperationEntity restartForRetry(UUID operationId) {
        var entity = repository.findById(operationId).orElseThrow();
        entity.restartForRetry(OffsetDateTime.now(clock));
        return repository.save(entity);
    }

    @Transactional
    void complete(UUID operationId, String responseText, UUID transactionId) {
        var entity = repository.findById(operationId).orElseThrow();
        entity.complete(responseText, transactionId, OffsetDateTime.now(clock));
        repository.save(entity);
    }

    @Transactional
    void fail(UUID operationId, AiIntegrationException.Stage stage) {
        var entity = repository.findById(operationId).orElseThrow();
        entity.fail(stage, OffsetDateTime.now(clock));
        repository.save(entity);
    }

    @Transactional
    void markStage(UUID operationId, AudioCommandStage stage) {
        var entity = repository.findById(operationId).orElseThrow();
        entity.markStage(stage, OffsetDateTime.now(clock));
        repository.save(entity);
    }

    /**
     * Converts an abandoned PROCESSING row to FAILED with the failureStage the
     * policy assigns for its currentStage. Re-checks status and abandonment
     * against a fresh read (not the caller's possibly-stale snapshot), so a row
     * the owning request just completed/failed concurrently is left untouched.
     * Each call is its own transaction, so an {@code OptimisticLockingFailureException}
     * here only affects this one row - see {@code
     * IdempotencyCleanupService#recoverAbandonedProcessing}.
     *
     * @return true if this call actually recovered the row, false if it was no
     * longer PROCESSING/abandoned by the time of the fresh read (nothing to do).
     */
    @Transactional
    boolean recoverAbandonedProcessing(UUID operationId) {
        var entity = repository.findById(operationId).orElse(null);
        if (entity == null || entity.getStatus() != AudioCommandStatus.PROCESSING
                || !policy.isProcessingAbandoned(entity)) {
            return false;
        }
        entity.fail(policy.failureStageForAbandoned(entity), OffsetDateTime.now(clock));
        repository.save(entity);
        return true;
    }

    @Transactional(readOnly = true)
    List<AudioCommandOperationEntity> findAbandonedProcessing(OffsetDateTime threshold, int batchSize) {
        return repository.findByStatusAndUpdatedAtBefore(AudioCommandStatus.PROCESSING, threshold, PageRequest.of(0, batchSize));
    }

    @Transactional(readOnly = true)
    List<AudioCommandOperationEntity> findExpired(AudioCommandStatus status, OffsetDateTime threshold, int batchSize) {
        return repository.findByStatusAndUpdatedAtBefore(status, threshold, PageRequest.of(0, batchSize));
    }

    /**
     * Deletes only rows still matching status+threshold at delete time - see
     * {@link AudioCommandOperationRepository#deleteExpiredBatch}.
     */
    @Transactional
    int deleteExpiredBatch(AudioCommandStatus status, OffsetDateTime threshold, List<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return repository.deleteExpiredBatch(status, threshold, ids);
    }

    /**
     * Single-row convenience over {@link #deleteExpiredBatch} used by the
     * opportunistic check in {@code AudioCommandIdempotencyService#begin}, so a
     * COMPLETED/FAILED row found expired during a request is removed
     * immediately rather than waiting for the next scheduler tick.
     */
    @Transactional
    boolean deleteIfStillExpired(UUID operationId, AudioCommandStatus status, OffsetDateTime threshold) {
        return repository.deleteExpiredBatch(status, threshold, List.of(operationId)) > 0;
    }
}

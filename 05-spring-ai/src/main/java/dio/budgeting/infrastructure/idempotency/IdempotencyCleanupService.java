package dio.budgeting.infrastructure.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * DB-only maintenance for {@code audio_command_operation} (TASK-010): recovers
 * PROCESSING rows abandoned past {@code app.idempotency.processing-timeout},
 * then deletes COMPLETED/FAILED rows past their retention window. Used by
 * {@code IdempotencyCleanupScheduler} (periodic) - the same policy also backs
 * the opportunistic check in {@code AudioCommandIdempotencyService#begin}, via
 * the shared per-row methods on {@code AudioCommandOperationStore}, so the two
 * paths can never disagree on what counts as abandoned/expired.
 *
 * <p>Never calls OpenAI, never reprocesses audio, never touches financial
 * transactions - only status/timestamps on this one table.
 */
@Component
public class IdempotencyCleanupService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupService.class);

    private final AudioCommandOperationStore store;
    private final IdempotencyExpirationPolicy policy;
    private final IdempotencyProperties properties;

    public IdempotencyCleanupService(AudioCommandOperationStore store, IdempotencyExpirationPolicy policy,
                                      IdempotencyProperties properties) {
        this.store = store;
        this.policy = policy;
        this.properties = properties;
    }

    /**
     * Converts abandoned PROCESSING rows to FAILED, one short transaction per
     * row (via {@code AudioCommandOperationStore#recoverAbandonedProcessing}),
     * so an optimistic-lock conflict on one row - e.g. the owning request just
     * completed it concurrently - never aborts the whole batch; that row is
     * simply skipped and reevaluated on the next run.
     *
     * @return how many rows were actually recovered.
     */
    public int recoverAbandonedProcessing() {
        var candidates = store.findAbandonedProcessing(policy.processingAbandonedThreshold(), properties.getBatchSize());
        int recovered = 0;
        for (var candidate : candidates) {
            try {
                if (store.recoverAbandonedProcessing(candidate.getId())) {
                    recovered++;
                }
            }
            catch (OptimisticLockingFailureException conflict) {
                log.debug("Conflito otimista ao recuperar operação abandonada; será reavaliada na próxima execução.");
            }
        }
        return recovered;
    }

    /**
     * Deletes COMPLETED/FAILED rows past retention. The delete itself re-checks
     * status+updatedAt in its WHERE clause (see {@code
     * AudioCommandOperationRepository#deleteExpiredBatch}), so a row that
     * changed state between the select and the delete (e.g. a FAILED row
     * retried back to PROCESSING) is silently skipped rather than deleted.
     *
     * @return how many rows were actually removed.
     */
    public int cleanupExpired() {
        int batchSize = properties.getBatchSize();
        var completedIds = idsOf(store.findExpired(AudioCommandStatus.COMPLETED, policy.completedExpirationThreshold(), batchSize));
        var failedIds = idsOf(store.findExpired(AudioCommandStatus.FAILED, policy.failedExpirationThreshold(), batchSize));

        int removed = 0;
        if (!completedIds.isEmpty()) {
            removed += store.deleteExpiredBatch(AudioCommandStatus.COMPLETED, policy.completedExpirationThreshold(), completedIds);
        }
        if (!failedIds.isEmpty()) {
            removed += store.deleteExpiredBatch(AudioCommandStatus.FAILED, policy.failedExpirationThreshold(), failedIds);
        }
        return removed;
    }

    private static List<UUID> idsOf(List<AudioCommandOperationEntity> entities) {
        return entities.stream().map(AudioCommandOperationEntity::getId).toList();
    }
}

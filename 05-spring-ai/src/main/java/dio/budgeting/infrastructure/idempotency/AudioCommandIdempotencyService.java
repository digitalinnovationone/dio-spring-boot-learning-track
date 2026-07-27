package dio.budgeting.infrastructure.idempotency;

import dio.budgeting.infrastructure.ai.AiIntegrationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Decides, for a given {@code Idempotency-Key} + payload fingerprint,
 * whether POST /transactions/ai must run the full AI pipeline or can replay
 * an already-completed result. See {@link IdempotencyDecision}.
 *
 * <p>Concurrency: two simultaneous requests with the same brand-new key can
 * both race past the initial {@code findByKey} check. The unique constraint
 * on {@code idempotency_key} (enforced by the database, not by this
 * in-memory check) makes one of the two {@code insertNew} calls fail with
 * {@link DataIntegrityViolationException}; that request re-reads the row the
 * winner just created and re-evaluates it as an existing operation (almost
 * certainly still PROCESSING, so it reports {@code IN_PROGRESS}).
 *
 * <p>TASK-010: {@code begin} also applies the expiration/recovery policy
 * opportunistically (not just via {@code IdempotencyCleanupScheduler}), so an
 * abandoned or expired operation is resolved on the very next request instead
 * of waiting for the next scheduler tick. See {@link IdempotencyExpirationPolicy}
 * for the shared rules and {@code AudioCommandOperationStore} for the
 * short-transaction primitives both this service and the scheduler build on.
 * A caveat documented in the README applies: the idempotency guarantee only
 * holds within the configured retention window - reusing a key after its
 * operation has expired and been removed starts a brand-new operation.
 */
@Service
public class AudioCommandIdempotencyService {

    private final AudioCommandOperationStore store;
    private final IdempotencyExpirationPolicy policy;

    public AudioCommandIdempotencyService(AudioCommandOperationStore store, IdempotencyExpirationPolicy policy) {
        this.store = store;
        this.policy = policy;
    }

    public IdempotencyDecision begin(String idempotencyKey, String payloadFingerprint) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return beginAfterValidation(idempotencyKey, payloadFingerprint, true);
    }

    private IdempotencyDecision beginAfterValidation(String idempotencyKey, String payloadFingerprint,
                                                       boolean mayAttemptInsert) {
        var existing = store.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), payloadFingerprint);
        }
        if (!mayAttemptInsert) {
            // Lost the race, and the winner's row is still not visible to us
            // (e.g. its own transaction hasn't committed yet). Safe default:
            // treat it the same as an in-progress operation rather than retry.
            throw new IdempotencyException(IdempotencyException.Reason.IN_PROGRESS,
                    "Uma operação com esta chave já está em processamento.");
        }
        return startNew(idempotencyKey, payloadFingerprint);
    }

    private IdempotencyDecision startNew(String idempotencyKey, String payloadFingerprint) {
        try {
            var created = store.insertNew(idempotencyKey, payloadFingerprint);
            return new IdempotencyDecision.Start(created.getId());
        }
        catch (DataIntegrityViolationException raceLost) {
            return beginAfterValidation(idempotencyKey, payloadFingerprint, false);
        }
    }

    private IdempotencyDecision resolveExisting(AudioCommandOperationEntity entity, String payloadFingerprint) {
        if (!entity.getPayloadFingerprint().equals(payloadFingerprint)) {
            throw new IdempotencyException(IdempotencyException.Reason.PAYLOAD_CONFLICT,
                    "A chave de idempotência já foi utilizada com outro arquivo.");
        }
        return switch (entity.getStatus()) {
            case PROCESSING -> resolveProcessing(entity, payloadFingerprint);
            case COMPLETED -> resolveCompleted(entity, payloadFingerprint);
            case FAILED -> resolveFailed(entity, payloadFingerprint);
        };
    }

    /**
     * A still-fresh PROCESSING row blocks as before. An abandoned one (past
     * app.idempotency.processing-timeout) is recovered right here instead of
     * waiting for the scheduler: {@code recoverAbandonedProcessing} converts it
     * to FAILED with the failureStage the currentStage warrants, then the usual
     * FAILED retry policy (below, in {@link #resolveFailed}) decides whether a
     * retry is safe.
     */
    private IdempotencyDecision resolveProcessing(AudioCommandOperationEntity entity, String payloadFingerprint) {
        if (!policy.isProcessingAbandoned(entity)) {
            throw new IdempotencyException(IdempotencyException.Reason.IN_PROGRESS,
                    "Uma operação com esta chave já está em processamento.");
        }
        boolean recovered = store.recoverAbandonedProcessing(entity.getId());
        if (!recovered) {
            // Lost the race (e.g. the scheduler, or another request, resolved it
            // first): re-read and re-resolve against whatever state won.
            var fresh = store.findByKey(entity.getIdempotencyKey())
                    .orElseThrow(() -> new IdempotencyException(IdempotencyException.Reason.IN_PROGRESS,
                            "Uma operação com esta chave já está em processamento."));
            return resolveExisting(fresh, payloadFingerprint);
        }
        var recoveredEntity = store.findByKey(entity.getIdempotencyKey()).orElseThrow();
        return resolveFailed(recoveredEntity, payloadFingerprint);
    }

    /**
     * Replay stays available for the whole retention window. Once expired, the
     * row is removed (freeing the key) and the request proceeds as a brand new
     * operation - see the class-level javadoc about the limited guarantee.
     */
    private IdempotencyDecision resolveCompleted(AudioCommandOperationEntity entity, String payloadFingerprint) {
        if (!policy.isCompletedExpired(entity)) {
            return new IdempotencyDecision.Replay(entity.getResponseText(), entity.getTransactionId());
        }
        store.deleteIfStillExpired(entity.getId(), AudioCommandStatus.COMPLETED, policy.completedExpirationThreshold());
        return beginAfterValidation(entity.getIdempotencyKey(), payloadFingerprint, true);
    }

    private IdempotencyDecision resolveFailed(AudioCommandOperationEntity entity, String payloadFingerprint) {
        if (policy.isFailedExpired(entity)) {
            store.deleteIfStillExpired(entity.getId(), AudioCommandStatus.FAILED, policy.failedExpirationThreshold());
            return beginAfterValidation(entity.getIdempotencyKey(), payloadFingerprint, true);
        }
        if (entity.getFailureStage() != AiIntegrationException.Stage.TRANSCRIPTION) {
            // CHAT or SPEECH failure: Tool Calling may already have persisted a
            // transaction before the failure - never safe to reprocess automatically.
            throw new IdempotencyException(IdempotencyException.Reason.RETRY_NOT_ALLOWED,
                    "Esta chave de idempotência falhou em uma etapa que pode já ter persistido uma "
                            + "transação. Utilize uma nova chave para tentar novamente.");
        }
        try {
            var restarted = store.restartForRetry(entity.getId());
            return new IdempotencyDecision.Start(restarted.getId());
        }
        catch (OptimisticLockingFailureException concurrentRetry) {
            throw new IdempotencyException(IdempotencyException.Reason.IN_PROGRESS,
                    "Uma operação com esta chave já está em processamento.");
        }
    }

    public void complete(UUID operationId, String responseText, UUID transactionId) {
        store.complete(operationId, responseText, transactionId);
    }

    public void fail(UUID operationId, AiIntegrationException.Stage stage) {
        store.fail(operationId, stage);
    }

    /**
     * Records the stage the current processing attempt is about to enter
     * (TASK-010), so an abandoned PROCESSING row can later be classified as
     * safe or unsafe to retry - see {@link AudioCommandStage}.
     */
    public void markStage(UUID operationId, AiIntegrationException.Stage stage) {
        store.markStage(operationId, AudioCommandStage.valueOf(stage.name()));
    }
}

package dio.budgeting.infrastructure.idempotency;

/**
 * Stage the CURRENT (or last) processing attempt has reached (TASK-010) -
 * distinct from {@code failureStage} (only meaningful once an operation has
 * actually failed). Set on {@code AudioCommandOperationEntity.currentStage}
 * before each external call in {@code AiTransactionProcessor#process}, so an
 * abandoned PROCESSING row can be classified without guessing:
 *
 * <p>REGISTERED/TRANSCRIPTION - Tool Calling has not run yet, safe to retry
 * after abandonment. CHAT/SPEECH - Tool Calling may already have persisted a
 * transaction, never safe to retry automatically.
 */
public enum AudioCommandStage {
    REGISTERED,
    TRANSCRIPTION,
    CHAT,
    SPEECH
}

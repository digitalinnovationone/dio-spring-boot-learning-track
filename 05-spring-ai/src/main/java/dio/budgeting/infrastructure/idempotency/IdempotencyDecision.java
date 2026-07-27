package dio.budgeting.infrastructure.idempotency;

import java.util.UUID;

/**
 * Result of {@code AudioCommandIdempotencyService.begin(...)}: either a brand
 * new (or safely retried) operation to process, or a completed operation
 * whose audio can be regenerated from the stored response text without
 * repeating transcription/chat/Tool Calling.
 */
public sealed interface IdempotencyDecision {

    record Start(UUID operationId) implements IdempotencyDecision {
    }

    record Replay(String responseText, UUID transactionId) implements IdempotencyDecision {
    }
}

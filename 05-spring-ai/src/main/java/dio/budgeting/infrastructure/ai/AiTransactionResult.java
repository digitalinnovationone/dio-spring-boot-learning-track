package dio.budgeting.infrastructure.ai;

import java.util.UUID;

/**
 * {@code transactionId} is always null for now: the ID that Tool Calling's
 * persist-transaction produces is only visible as the tool's own return
 * value, never surfaced through {@code ChatClient.CallResponseSpec.content()}
 * (the final natural-language reply). Capturing it reliably would require
 * either duplicating the @Tool annotation on a capturing wrapper (drifting
 * from the single source of truth in PersistTransactionUseCase) or parsing
 * the AI's text response (explicitly unsafe). Left nullable for a future task.
 */
public record AiTransactionResult(byte[] audio, String responseText, UUID transactionId) {
}

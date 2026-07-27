package dio.budgeting.infrastructure.idempotency;

public class IdempotencyException extends RuntimeException {

    public enum Reason {
        MISSING_KEY,
        INVALID_KEY,
        PAYLOAD_CONFLICT,
        IN_PROGRESS,
        RETRY_NOT_ALLOWED
    }

    private final Reason reason;

    public IdempotencyException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
